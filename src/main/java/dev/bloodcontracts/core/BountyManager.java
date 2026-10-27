package dev.bloodcontracts.core;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.advancement.BountyTrigger;
import dev.bloodcontracts.attribution.DamageLedger;
import dev.bloodcontracts.attribution.KillAttributor;
import dev.bloodcontracts.attribution.ResponsibleResolver;
import dev.bloodcontracts.config.BCConfig;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The only entry point that changes Blood Contracts state. Commands, network handlers and game events all call it, so
 * there is a single path to test. Must only be called on the server thread.
 * <p>
 * Escrow invariant: an engaged item is always in exactly one place — an active bounty, a pending delivery, or a
 * player (inventory or dropped at their feet). Heart invariant: every HP a target loses is gained by a sponsor
 * (balances are computed from {@link HeartTransfer}s). Every operation validates first and mutates only once every
 * check passed, so a refused operation changes nothing.
 */
public final class BountyManager {
    public static final ResourceLocation HEART_MODIFIER = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "heart_balance");
    /** Periodic work (cancel notices, heart regeneration, immunities) every ~20 s. */
    private static final int PERIODIC_TICKS = 400;
    private static final double EPSILON = 1e-9;

    private static @Nullable BountyManager instance;

    private final MinecraftServer server;
    private final DamageLedger ledger = new DamageLedger();
    private TimeSource time = TimeSource.SYSTEM;
    private @Nullable Set<UUID> activeTargets;

    private BountyManager(MinecraftServer server) {
        this.server = server;
    }

    public static BountyManager get(MinecraftServer server) {
        BountyManager current = instance;
        if (current == null || current.server != server) {
            current = new BountyManager(server);
            instance = current;
        }
        return current;
    }

    /** Forgets the per-server instance (server stopped). Persistent data lives in {@link BountySavedData}. */
    public static void release() {
        instance = null;
    }

    // ---------------------------------------------------------------- queries

    public BountySavedData data() {
        return server.overworld().getDataStorage().computeIfAbsent(BountySavedData.FACTORY, BountySavedData.NAME);
    }

    public DamageLedger ledger() {
        return ledger;
    }

    public long gameTime() {
        return server.overworld().getGameTime();
    }

    /** Wall-clock time from the {@link TimeSource}. */
    public long now() {
        return time.nowMillis();
    }

    public List<Bounty> activeBounties() {
        return List.copyOf(data().activeBounties());
    }

    public Optional<Bounty> findActive(UUID id) {
        return data().active(id);
    }

    /** Creation order. */
    public List<Bounty> activeOn(UUID target) {
        return data().activeBounties().stream().filter(b -> b.target().equals(target)).toList();
    }

    public List<Bounty> activeBy(UUID sponsor) {
        return data().activeBounties().stream().filter(b -> b.sponsor().equals(sponsor)).toList();
    }

    /** Newest first. */
    public List<Bounty> history() {
        List<Bounty> h = new ArrayList<>(data().history());
        java.util.Collections.reverse(h);
        return h;
    }

    public Optional<Bounty> findAny(UUID id) {
        return data().active(id).or(() -> data().history().stream().filter(b -> b.id().equals(id)).findFirst());
    }

    public List<PendingDelivery> deliveriesFor(UUID player) {
        return data().deliveries().stream().filter(d -> d.recipient().equals(player)).toList();
    }

    public boolean hasActiveBountyOn(UUID target) {
        Set<UUID> targets = activeTargets;
        if (targets == null) {
            targets = new HashSet<>();
            for (Bounty b : data().activeBounties()) {
                targets.add(b.target());
            }
            activeTargets = targets;
        }
        return targets.contains(target);
    }

    public boolean isKnownPlayer(UUID uuid) {
        if (server.getPlayerList().getPlayer(uuid) != null || data().knownName(uuid).isPresent()) {
            return true;
        }
        GameProfileCache cache = server.getProfileCache();
        return cache != null && cache.get(uuid).isPresent();
    }

    /** Resolves a player name (case-insensitive) among online players, players who ever joined, and the profile cache. */
    public Optional<UUID> findKnownPlayer(String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        for (var e : data().knownPlayers().entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return Optional.of(e.getKey());
            }
        }
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(name).map(com.mojang.authlib.GameProfile::getId);
    }

    public String nameOf(UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Optional<String> known = data().knownName(uuid);
        if (known.isPresent()) {
            return known.get();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    public boolean isOnline(UUID player) {
        return server.getPlayerList().getPlayer(player) != null;
    }

    /** End of the target's immunity, if it is still running. */
    public Optional<Long> immuneUntil(UUID target) {
        long now = now();
        return data().immuneUntil(target).filter(until -> until > now);
    }

    public int heartBalanceHp(UUID player) {
        return data().balance(player);
    }

    /** Could {@code viewer} collect this bounty by killing its target right now (immunity aside)? */
    public boolean canCollect(UUID viewer, Bounty bounty) {
        return bounty.isActive() && bounty.isEligibleHunter(viewer);
    }

    // ---------------------------------------------------------------- creation

    /** Places a public bounty funded by the stack in {@code source[slot]} (command: main hand). */
    public BountyResult<Bounty> create(ServerPlayer sponsor, UUID target, Container source, int slot) {
        return create(sponsor, target, BountyType.PUBLIC, List.of(), source, List.of(slot));
    }

    /** Public bounty funded by every non-empty stack among {@code source[slots]}. */
    public BountyResult<Bounty> create(ServerPlayer sponsor, UUID target, Container source, List<Integer> slots) {
        return create(sponsor, target, BountyType.PUBLIC, List.of(), source, slots);
    }

    /**
     * Places a bounty funded by every non-empty stack among {@code source[slots]} (creation menu: 1 slot, or up to
     * {@link Bounty#MAX_REWARD_STACKS} when expanded). All the stacks move to escrow in this call; if any check fails,
     * nothing moves.
     *
     * @param hunters who may collect a {@code PRIVATE} bounty (ignored for {@code PUBLIC})
     */
    public BountyResult<Bounty> create(ServerPlayer sponsor, UUID target, BountyType type, Collection<UUID> hunters,
                                       Container source, List<Integer> slots) {
        if (slots.isEmpty() || slots.size() > Bounty.MAX_REWARD_STACKS
                || slots.stream().anyMatch(i -> i < 0 || i >= source.getContainerSize())
                || slots.stream().distinct().count() != slots.size()) {
            return BountyResult.fail(BountyError.INVALID_SLOT);
        }
        UUID sponsorId = sponsor.getUUID();
        if (sponsorId.equals(target)) {
            return BountyResult.fail(BountyError.SELF_TARGET);
        }
        if (!isKnownPlayer(target)) {
            return BountyResult.fail(BountyError.UNKNOWN_TARGET);
        }
        List<UUID> hunterList = type == BountyType.PRIVATE ? List.copyOf(new LinkedHashSet<>(hunters)) : List.of();
        if (type == BountyType.PRIVATE) {
            BountyError invalid = validateHunters(sponsorId, target, hunterList, 0);
            if (invalid != null) {
                return BountyResult.fail(invalid);
            }
        }
        List<Integer> filled = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() <= 0 || stack.getCount() > stack.getMaxStackSize()) {
                return BountyResult.fail(BountyError.INVALID_QUANTITY);
            }
            if (BCConfig.rewardBlacklist().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return BountyResult.fail(BountyError.BLACKLISTED);
            }
            filled.add(slot);
        }
        if (filled.isEmpty()) {
            return BountyResult.fail(BountyError.EMPTY_SLOT);
        }
        List<Bounty> mine = activeBy(sponsorId);
        // One active bounty per sponsor-target pair (even under cancel notice): otherwise stacked bounties would steal
        // several hearts on one death.
        if (mine.stream().anyMatch(b -> b.target().equals(target))) {
            return BountyResult.fail(BountyError.PAIR_EXISTS);
        }
        if (mine.size() >= BCConfig.maxActiveBountiesPerSponsor()) {
            return BountyResult.fail(BountyError.LIMIT_REACHED);
        }

        // Every check passed: move every stack into escrow in one go.
        List<ItemStack> rewards = filled.stream().map(source::getItem).toList();
        Bounty bounty = Bounty.create(UUID.randomUUID(), sponsorId, target, rewards, now(), type, hunterList);
        for (int slot : filled) {
            source.setItem(slot, ItemStack.EMPTY);
        }
        source.setChanged();
        BountySavedData data = data();
        data.putActive(bounty);
        data.setDirty();
        activeTargets = null;
        debug("created {} bounty {} sponsor={} target={} hunters={} rewards={}", type, bounty.id(), sponsorId, target, hunterList, bounty.summary());

        BountyTrigger.fire(sponsor, BountyTrigger.Event.PLACED, 1);
        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(target);
        if (onlineTarget != null) {
            BountyNotifier.get().bountyPlaced(onlineTarget, bounty, nameOf(sponsorId));
            BountyTrigger.fire(onlineTarget, BountyTrigger.Event.WANTED, activeOn(target).size());
        }
        return BountyResult.ok(bounty);
    }

    /** Hunters must be known players, not the sponsor nor the target, and fit in {@code maxHunterListSize}. */
    private @Nullable BountyError validateHunters(UUID sponsor, UUID target, List<UUID> hunters, int alreadyListed) {
        if (hunters.isEmpty() || alreadyListed + hunters.size() > BCConfig.maxHunterListSize()) {
            return BountyError.INVALID_HUNTERS;
        }
        for (UUID h : hunters) {
            if (h.equals(sponsor) || h.equals(target) || !isKnownPlayer(h)) {
                return BountyError.INVALID_HUNTERS;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- widening (never narrowing)

    /** Adds hunters to the sponsor's own active private bounty. Nobody can ever be removed. */
    public BountyResult<Bounty> addHunters(UUID actor, UUID bountyId, Collection<UUID> added) {
        BountyResult<Bounty> owned = ownedActive(actor, bountyId);
        if (!owned.isOk()) {
            return owned;
        }
        Bounty bounty = owned.value();
        if (bounty.type() != BountyType.PRIVATE) {
            return BountyResult.fail(BountyError.ALREADY_PUBLIC);
        }
        List<UUID> fresh = added.stream().distinct().filter(h -> !bounty.hunters().contains(h)).toList();
        BountyError invalid = validateHunters(bounty.sponsor(), bounty.target(), fresh, bounty.hunters().size());
        if (invalid != null) {
            return BountyResult.fail(invalid);
        }
        Bounty widened = bounty.withHunters(fresh);
        replaceActive(widened);
        debug("bounty {}: hunters added {}", bountyId, fresh);
        return BountyResult.ok(widened);
    }

    /** PRIVATE → PUBLIC on the sponsor's own active bounty. The reverse does not exist. */
    public BountyResult<Bounty> makePublic(UUID actor, UUID bountyId) {
        BountyResult<Bounty> owned = ownedActive(actor, bountyId);
        if (!owned.isOk()) {
            return owned;
        }
        if (owned.value().type() == BountyType.PUBLIC) {
            return BountyResult.fail(BountyError.ALREADY_PUBLIC);
        }
        Bounty widened = owned.value().madePublic();
        replaceActive(widened);
        debug("bounty {} made public", bountyId);
        return BountyResult.ok(widened);
    }

    private BountyResult<Bounty> ownedActive(UUID actor, UUID bountyId) {
        Optional<Bounty> found = data().active(bountyId);
        if (found.isEmpty()) {
            return BountyResult.fail(findAny(bountyId).isPresent() ? BountyError.NOT_ACTIVE : BountyError.NOT_FOUND);
        }
        if (!found.get().sponsor().equals(actor)) {
            return BountyResult.fail(BountyError.NOT_OWNER);
        }
        return BountyResult.ok(found.get());
    }

    private void replaceActive(Bounty updated) {
        BountySavedData data = data();
        data.putActive(updated);
        data.setDirty();
    }

    // ---------------------------------------------------------------- cancellation (notice)

    /** Sponsor: starts the cancel notice. Operator: cancels immediately (maintenance). */
    public BountyResult<Bounty> cancel(UUID actor, UUID bountyId, boolean asOperator) {
        return asOperator ? adminCancel(bountyId) : requestCancel(actor, bountyId);
    }

    /**
     * Starts the cancel notice: the bounty stays active and collectable for {@code cancelNoticeMinutes}, then goes back
     * to its sponsor. Asking again changes nothing (the notice is never shortened).
     */
    public BountyResult<Bounty> requestCancel(UUID actor, UUID bountyId) {
        BountyResult<Bounty> owned = ownedActive(actor, bountyId);
        if (!owned.isOk()) {
            return owned;
        }
        Bounty bounty = owned.value();
        if (bounty.isCancelPending()) {
            return BountyResult.ok(bounty);
        }
        Bounty pending = bounty.withCancelAt(now() + BCConfig.cancelNoticeMillis());
        replaceActive(pending);
        debug("bounty {}: cancel requested, ends at {}", bountyId, pending.cancelAt());
        if (BCConfig.cancelNoticeMillis() <= 0) {
            processExpiredNotices();
            return findAny(bountyId).map(BountyResult::ok).orElse(BountyResult.ok(pending));
        }
        return BountyResult.ok(pending);
    }

    /** Withdraws a pending cancellation: the bounty is normal again. */
    public BountyResult<Bounty> resume(UUID actor, UUID bountyId) {
        BountyResult<Bounty> owned = ownedActive(actor, bountyId);
        if (!owned.isOk()) {
            return owned;
        }
        if (!owned.value().isCancelPending()) {
            return BountyResult.fail(BountyError.NOT_CANCELLING);
        }
        Bounty resumed = owned.value().resumed();
        replaceActive(resumed);
        debug("bounty {}: cancellation withdrawn", bountyId);
        return BountyResult.ok(resumed);
    }

    /** Operator maintenance: immediate cancellation, refund to the sponsor. */
    public BountyResult<Bounty> adminCancel(UUID bountyId) {
        Optional<Bounty> found = data().active(bountyId);
        if (found.isEmpty()) {
            return BountyResult.fail(findAny(bountyId).isPresent() ? BountyError.NOT_ACTIVE : BountyError.NOT_FOUND);
        }
        return BountyResult.ok(close(found.get()));
    }

    /** Cancels (with refund) every bounty whose notice is over. Returns how many. Periodic and at server start. */
    public int processExpiredNotices() {
        long now = now();
        List<Bounty> due = data().activeBounties().stream()
                .filter(b -> b.cancelAt() != null && b.cancelAt() <= now).toList();
        due.forEach(this::close);
        return due.size();
    }

    private Bounty close(Bounty bounty) {
        BountySavedData data = data();
        Bounty closed = bounty.cancel(now());
        data.removeActive(bounty.id());
        data.addHistory(closed, BCConfig.historySize());
        for (ItemStack refund : bounty.rewards()) {
            data.addDelivery(new PendingDelivery(UUID.randomUUID(), bounty.sponsor(), refund.copy(), bounty.id(), PendingDelivery.Reason.REFUND));
        }
        data.setDirty();
        activeTargets = null;
        debug("bounty {} cancelled, refund to {}", bounty.id(), bounty.sponsor());
        notifyRewardsAvailable(bounty.sponsor());
        return closed;
    }

    // ---------------------------------------------------------------- combat

    /** {@code LivingDamageEvent.Post}: remembers player damage on bounty targets. */
    public void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || !hasActiveBountyOn(victim.getUUID())) {
            return;
        }
        Optional<UUID> attacker = ResponsibleResolver.resolve(source, BCConfig.creditTamedPets());
        if (attacker.isEmpty() || attacker.get().equals(victim.getUUID())) {
            return;
        }
        String type = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?");
        ledger.record(victim.getUUID(), new DamageLedger.Entry(attacker.get(), gameTime(), amount, type), BCConfig.attributionWindowTicks());
    }

    /**
     * {@code LivingDeathEvent}: if the target is not immune and a hunter is reliably responsible, every active bounty on
     * the victim that they are eligible for is claimed. A replayed event finds no active bounty left and does nothing.
     *
     * @return the claimed bounties (empty if nobody was credited)
     */
    public List<Bounty> onDeath(LivingEntity victim, DamageSource source) {
        UUID victimId = victim.getUUID();
        if (!hasActiveBountyOn(victimId)) {
            ledger.clear(victimId);
            return List.of();
        }
        KillAttributor.Input input = new KillAttributor.Input(
                victimId,
                ResponsibleResolver.resolve(source, BCConfig.creditTamedPets()),
                ResponsibleResolver.classify(source),
                gameTime(),
                ledger.entries(victimId, gameTime(), BCConfig.attributionWindowTicks()),
                BCConfig.attributionWindowTicks());
        Optional<UUID> hunter = KillAttributor.attribute(input);
        ledger.clear(victimId);
        String type = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?");
        if (hunter.isEmpty()) {
            debug("death of {} ({}): no reliable hunter, bounties stay active", victimId, type);
            return List.of();
        }
        if (immuneUntil(victimId).isPresent()) {
            debug("death of {} ({}): target immune, bounties stay active", victimId, type);
            return List.of();
        }
        debug("death of {} ({}): credited to {}", victimId, type, hunter.get());
        List<Bounty> claimed = claimAll(victimId, hunter.get());
        ServerPlayer hunterPlayer = server.getPlayerList().getPlayer(hunter.get());
        if (!claimed.isEmpty() && hunterPlayer != null) {
            BountyTrigger.fire(hunterPlayer, BountyTrigger.Event.CLAIMED, claimed.size());
            if (isArtillery(source)) {
                BountyTrigger.fire(hunterPlayer, BountyTrigger.Event.ARTILLERY, claimed.size());
            }
        }
        return claimed;
    }

    /** A cannon kill: Create Big Cannons damage, or an explosion whose direct entity is a marked (cannon) munition. */
    private static boolean isArtillery(DamageSource source) {
        boolean cbcType = source.typeHolder().unwrapKey().map(k -> "createbigcannons".equals(k.location().getNamespace())).orElse(false);
        return cbcType || dev.bloodcontracts.attribution.BCAttachments.responsibleOf(source.getDirectEntity()).isPresent();
    }

    /**
     * Claims every active bounty on {@code target} that {@code hunter} is eligible for (including those under cancel
     * notice), in creation order; moves at most {@code maxHeartsLostPerDeath} from the target to the oldest sponsor who
     * has room; makes the target immune; counts the pair. One mutation, then {@code setDirty()}, then notifications.
     */
    @VisibleForTesting
    public List<Bounty> claimAll(UUID target, UUID hunter) {
        BountySavedData data = data();
        List<Bounty> claimed = new ArrayList<>();
        long now = now();
        for (Bounty bounty : activeOn(target)) {
            if (!bounty.isEligibleHunter(hunter)) {
                continue;
            }
            Bounty closed = bounty.claim(hunter, now);
            data.removeActive(bounty.id());
            data.addHistory(closed, BCConfig.historySize());
            for (ItemStack reward : bounty.rewards()) {
                data.addDelivery(new PendingDelivery(UUID.randomUUID(), hunter, reward.copy(), bounty.id(), PendingDelivery.Reason.REWARD));
            }
            claimed.add(closed);
            debug("bounty {} claimed by {}", bounty.id(), hunter);
        }
        if (claimed.isEmpty()) {
            return claimed;
        }
        UUID heartTo = transferHeart(target, claimed, now);
        data.setImmuneUntil(target, now + BCConfig.targetImmunityMillis());
        data.addPairCount(hunter, target, claimed.size());
        data.setDirty();
        activeTargets = null;

        refreshHearts(target);
        if (heartTo != null) {
            refreshHearts(heartTo);
        }
        notifyRewardsAvailable(hunter);
        return claimed;
    }

    /**
     * Moves up to {@code maxHeartsLostPerDeath} from the target to the sponsor of the oldest claimed bounty who has room,
     * within both bounds; nothing is created or destroyed. Returns who received it, if anyone.
     */
    private @Nullable UUID transferHeart(UUID target, List<Bounty> claimedInOrder, long now) {
        BountySavedData data = data();
        int toMove = BCConfig.maxHpLostPerDeath();
        if (toMove <= 0) {
            return null;
        }
        // Bring the target's gauge up to date before its debt changes (time so far counts at the old state).
        updateRegen(target, isOnline(target), now);
        boolean hadDebt = hasDebt(target);
        int targetRoom = data.balance(target) + BCConfig.maxHpLost();
        for (Bounty b : claimedInOrder) {
            int sponsorRoom = BCConfig.maxHpGained() - data.balance(b.sponsor());
            int amount = Math.min(toMove, Math.min(sponsorRoom, targetRoom));
            if (amount > 0) {
                data.addTransfer(new HeartTransfer(UUID.randomUUID(), target, b.sponsor(), amount, now));
                if (!hadDebt) {
                    data.setRegen(target, new BountySavedData.RegenGauge(0, now));
                }
                debug("{} HP moved from {} to {}", amount, target, b.sponsor());
                return b.sponsor();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- hearts: regeneration

    private boolean hasDebt(UUID target) {
        return data().transfers().stream().anyMatch(t -> t.from().equals(target));
    }

    /**
     * Brings a target's regeneration gauge up to {@code now}: the elapsed time counts at the online or offline rate,
     * every full point of gauge gives back 1 HP taken from the target's oldest transfer (its sponsor loses that HP).
     * Deterministic and catch-up safe: never more than what is due.
     */
    public void updateRegen(UUID target, boolean online, long now) {
        BountySavedData data = data();
        Optional<BountySavedData.RegenGauge> gauge = data.regen(target);
        if (gauge.isEmpty()) {
            return;
        }
        if (!hasDebt(target)) {
            data.setRegen(target, null);
            data.setDirty();
            return;
        }
        long elapsed = Math.max(0, now - gauge.get().lastUpdate());
        double progress = gauge.get().progress();
        if (BCConfig.heartRegenMode() == BCConfig.HeartRegenMode.PROGRESSIVE) {
            long step = online ? BCConfig.heartRegenOnlineStepMillis() : BCConfig.heartRegenOfflineStepMillis();
            progress += (double) elapsed / step;
        }
        Set<UUID> sponsorsPaidBack = new HashSet<>();
        while (progress >= 1 - EPSILON && hasDebt(target)) {
            progress = Math.max(0, progress - 1);
            HeartTransfer oldest = data.transfers().stream().filter(t -> t.from().equals(target)).findFirst().orElseThrow();
            data.replaceTransfer(oldest, oldest.remainingHp() > 1 ? oldest.minusOneHp() : null);
            sponsorsPaidBack.add(oldest.to());
        }
        data.setRegen(target, hasDebt(target) ? new BountySavedData.RegenGauge(progress, now) : null);
        data.setDirty();
        if (!sponsorsPaidBack.isEmpty()) {
            refreshHearts(target);
            sponsorsPaidBack.forEach(this::refreshHearts);
            debug("regen: {} got health back (sponsors {})", target, sponsorsPaidBack);
        }
    }

    /** Updates every gauge; {@code online} players at the online rate, the others at the offline rate. */
    public void updateAllRegen(boolean onlinePlayersAreOnline) {
        long now = now();
        for (UUID target : List.copyOf(data().regenGauges().keySet())) {
            updateRegen(target, onlinePlayersAreOnline && isOnline(target), now);
        }
    }

    // ---------------------------------------------------------------- hearts: attribute

    /** Re-applies the {@code bloodcontracts:heart_balance} max-health modifier of an online player, if any. */
    public void refreshHearts(UUID player) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            applyHeartModifier(p);
        }
    }

    /**
     * Sets our own (and only our own) transient {@code MAX_HEALTH} modifier to the player's balance; health is lowered to
     * the new maximum if needed, never to the point of killing.
     */
    public void applyHeartModifier(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        int balance = data().balance(player.getUUID());
        maxHealth.removeModifier(HEART_MODIFIER);
        if (balance != 0) {
            maxHealth.addTransientModifier(new AttributeModifier(HEART_MODIFIER, balance, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.isAlive() && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(Math.max(1, player.getMaxHealth()));
        }
    }

    /** Problems in the heart settings that would make the system pointless or unfair; logged at server start. */
    public static List<String> configWarnings() {
        List<String> warnings = new ArrayList<>();
        long online = BCConfig.heartRegenOnlineStepMillis();
        long offline = BCConfig.heartRegenOfflineStepMillis();
        long immunity = BCConfig.targetImmunityMillis();
        if (online * 2 <= immunity) {
            warnings.add("heartRegenOnlineStepMinutes x 2 <= targetImmunityMinutes: hearts come back by playing at least as fast as they can be lost, the heart system has no effect");
        }
        if (offline < online) {
            warnings.add("heartRegenOfflineStepMinutes < heartRegenOnlineStepMinutes: being offline would bring hearts back faster than playing");
        }
        return warnings;
    }

    // ---------------------------------------------------------------- rewards to claim

    /**
     * Hands over what is owed to {@code player}: one delivery, or all of them when {@code deliveryId} is empty.
     * Rewards and refunds wait in the board until claimed, so nothing ever lands in an inventory unannounced.
     *
     * @return the number of stacks handed over
     */
    public BountyResult<Integer> claimRewards(ServerPlayer player, Optional<UUID> deliveryId) {
        if (!player.isAlive() || player.hasDisconnected()) {
            return BountyResult.fail(BountyError.NOT_ALIVE);
        }
        List<PendingDelivery> owed = deliveriesFor(player.getUUID());
        if (deliveryId.isPresent()) {
            owed = owed.stream().filter(d -> d.id().equals(deliveryId.get())).toList();
            if (owed.isEmpty()) {
                return BountyResult.fail(BountyError.NOT_FOUND);
            }
        } else if (owed.isEmpty()) {
            return BountyResult.fail(BountyError.NOTHING_TO_CLAIM);
        }
        BountySavedData data = data();
        int handed = 0;
        for (PendingDelivery delivery : owed) {
            if (!data.removeDelivery(delivery)) {
                continue;
            }
            data.setDirty();
            give(player, delivery.stack().copy());
            handed++;
            debug("player {} claimed delivery {} ({}) of bounty {}", player.getUUID(), delivery.id(), delivery.reason(), delivery.bountyId());
        }
        return handed == 0 ? BountyResult.fail(BountyError.NOTHING_TO_CLAIM) : BountyResult.ok(handed);
    }

    private void notifyRewardsAvailable(UUID recipient) {
        ServerPlayer player = server.getPlayerList().getPlayer(recipient);
        if (player != null) {
            int owed = deliveriesFor(recipient).size();
            if (owed > 0) {
                BountyNotifier.get().rewardsAvailable(player, owed);
            }
        }
    }

    /** Puts the stack in the inventory; whatever does not fit is dropped at the player's feet. Never voids items. */
    private static void give(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        // Not Inventory#add: it silently voids overflow for creative players.
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot == -1) {
                slot = inventory.getFreeSlot();
            }
            if (slot == -1) {
                break;
            }
            int space = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize(stack)) - inventory.getItem(slot).getCount();
            if (space <= 0 || !inventory.add(slot, stack.split(space))) {
                break;
            }
        }
        if (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            drop.setNoPickUpDelay();
            drop.setTarget(player.getUUID());
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(0, 0, 0);
            player.level().addFreshEntity(drop);
        }
        player.inventoryMenu.broadcastChanges();
    }

    // ---------------------------------------------------------------- lifecycle hooks

    /**
     * {@code ServerStartedEvent}: registers players who already have a save on this world; finalizes cancel notices
     * that ran out while the server was down; brings every heart gauge up to date (the downtime counts as offline);
     * warns about inconsistent heart settings.
     */
    public void onServerStarted() {
        GameProfileCache cache = server.getProfileCache();
        int added = importKnownPlayers(listPlayerData(server.getWorldPath(LevelResource.PLAYER_DATA_DIR)),
                id -> cache == null ? Optional.empty() : cache.get(id).map(com.mojang.authlib.GameProfile::getName));
        if (added > 0) {
            BloodContracts.LOGGER.info("Blood Contracts: registered {} existing player(s) as possible targets", added);
        }
        int expired = processExpiredNotices();
        if (expired > 0) {
            BloodContracts.LOGGER.info("Blood Contracts: {} cancel notice(s) ended while the server was down", expired);
        }
        updateAllRegen(false);
        data().purgeImmunities(now());
        configWarnings().forEach(w -> BloodContracts.LOGGER.warn("Blood Contracts config: {}", w));
    }

    /** {@code ServerStoppingEvent}: online time up to now counts as online; the downtime will count as offline. */
    public void onServerStopping() {
        updateAllRegen(true);
    }

    /** Adds the players not known yet; never renames a known one. Returns how many were added. */
    @VisibleForTesting
    public int importKnownPlayers(Collection<UUID> players, Function<UUID, Optional<String>> names) {
        BountySavedData data = data();
        int added = 0;
        for (UUID id : players) {
            if (data.knownName(id).isPresent()) {
                continue;
            }
            Optional<String> name = names.apply(id).filter(n -> !n.isBlank());
            if (name.isPresent() && data.rememberPlayer(id, name.get())) {
                added++;
            }
        }
        if (added > 0) {
            data.setDirty();
        }
        return added;
    }

    /** UUIDs of the {@code <uuid>.dat} files in a playerdata directory (other files are ignored). */
    @VisibleForTesting
    public static List<UUID> listPlayerData(Path dir) {
        List<UUID> ids = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return ids;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                try {
                    ids.add(UUID.fromString(name.substring(0, name.length() - 4)));
                } catch (IllegalArgumentException ignored) {
                    // not a player file
                }
            }
        } catch (IOException e) {
            BloodContracts.LOGGER.warn("Blood Contracts: could not list {}", dir, e);
        }
        return ids;
    }

    /** {@code PlayerLoggedInEvent}. */
    public void onPlayerLogin(ServerPlayer player) {
        BountySavedData data = data();
        if (data.rememberPlayer(player.getUUID(), player.getGameProfile().getName())) {
            data.setDirty();
        }
        player.getGameProfile().getProperties().get("textures").stream().findFirst().ifPresent(textures -> {
            if (data.rememberSkin(player.getUUID(), textures)) {
                data.setDirty();
            }
        });
        // The time since the last update was spent offline.
        updateRegen(player.getUUID(), false, now());
        applyHeartModifier(player);
        notifyRewardsAvailable(player.getUUID());
        List<Bounty> onMe = activeOn(player.getUUID());
        if (!onMe.isEmpty()) {
            // Bounties may have been placed while they were away: they learn it on arrival.
            BountyNotifier.get().bountiesOnLogin(player, onMe);
            BountyTrigger.fire(player, BountyTrigger.Event.WANTED, onMe.size());
        }
    }

    /** {@code PlayerLoggedOutEvent}: the session counts as online time for regeneration. */
    public void onPlayerLogout(ServerPlayer player) {
        updateRegen(player.getUUID(), true, now());
        ledger.clear(player.getUUID());
    }

    /** {@code PlayerRespawnEvent}, {@code PlayerEvent.Clone}, {@code PlayerChangedDimensionEvent}. */
    public void onPlayerRespawnOrClone(ServerPlayer player) {
        applyHeartModifier(player);
    }

    public void tick() {
        long gt = gameTime();
        if (gt % 100 == 0) {
            ledger.purgeAll(gt, BCConfig.attributionWindowTicks());
        }
        if (gt % PERIODIC_TICKS == 0) {
            periodic();
        }
    }

    /** Every ~20 s: finalize ended notices, regenerate online targets, drop ended immunities. */
    @VisibleForTesting
    public void periodic() {
        processExpiredNotices();
        long now = now();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            updateRegen(p.getUUID(), true, now);
        }
        data().purgeImmunities(now);
    }

    // ---------------------------------------------------------------- tests

    /** Test-only: wipes every bounty, delivery, transfer, gauge, immunity and ledger entry; restores the system clock. */
    @VisibleForTesting
    public void resetForTests() {
        BountySavedData data = data();
        data.clearAll();
        data.setDirty();
        ledger.clearAll();
        activeTargets = null;
        time = TimeSource.SYSTEM;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyHeartModifier(p);
        }
    }

    /** Test-only: controllable wall clock. */
    @VisibleForTesting
    public void setTimeSourceForTests(TimeSource source) {
        time = source;
    }

    private static void debug(String message, Object... args) {
        if (BCConfig.debug()) {
            BloodContracts.LOGGER.info("[bounty] " + message, args);
        }
    }
}
