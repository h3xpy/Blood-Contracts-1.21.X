package dev.bloodcontracts.core;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.attribution.DamageLedger;
import dev.bloodcontracts.attribution.KillAttributor;
import dev.bloodcontracts.attribution.ResponsibleResolver;
import dev.bloodcontracts.config.BCConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The only entry point that changes bounty state. Commands, network handlers and game events all call it, so there
 * is a single path to test. Must only be called on the server thread.
 * <p>
 * Escrow invariant: an engaged item is always in exactly one place — an active bounty, a pending delivery, or a
 * player (inventory or dropped at their feet). Every operation validates first and mutates only once every check
 * passed, so a refused operation changes nothing.
 */
public final class BountyManager {
    private static @Nullable BountyManager instance;

    private final MinecraftServer server;
    private final DamageLedger ledger = new DamageLedger();
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

    public List<Bounty> activeBounties() {
        return List.copyOf(data().activeBounties());
    }

    public Optional<Bounty> findActive(UUID id) {
        return data().active(id);
    }

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

    // ---------------------------------------------------------------- creation

    /** Places a bounty funded by the stack in {@code source[slot]} (command: main hand). */
    public BountyResult<Bounty> create(ServerPlayer sponsor, UUID target, Container source, int slot) {
        return create(sponsor, target, source, List.of(slot));
    }

    /**
     * Places a bounty funded by every non-empty stack among {@code source[slots]} (creation menu: 1 slot, or up to
     * {@link Bounty#MAX_REWARD_STACKS} when expanded). All the stacks move to escrow in this call; if any check fails,
     * nothing moves.
     */
    public BountyResult<Bounty> create(ServerPlayer sponsor, UUID target, Container source, List<Integer> slots) {
        if (slots.isEmpty() || slots.size() > Bounty.MAX_REWARD_STACKS
                || slots.stream().anyMatch(i -> i < 0 || i >= source.getContainerSize())
                || slots.stream().distinct().count() != slots.size()) {
            return BountyResult.fail(BountyError.INVALID_SLOT);
        }
        if (sponsor.getUUID().equals(target)) {
            return BountyResult.fail(BountyError.SELF_TARGET);
        }
        if (!isKnownPlayer(target)) {
            return BountyResult.fail(BountyError.UNKNOWN_TARGET);
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
        if (activeBy(sponsor.getUUID()).size() >= BCConfig.maxActiveBountiesPerSponsor()) {
            return BountyResult.fail(BountyError.LIMIT_REACHED);
        }

        // Every check passed: move every stack into escrow in one go.
        List<ItemStack> rewards = filled.stream().map(source::getItem).toList();
        Bounty bounty = Bounty.create(UUID.randomUUID(), sponsor.getUUID(), target, rewards, System.currentTimeMillis());
        for (int slot : filled) {
            source.setItem(slot, ItemStack.EMPTY);
        }
        source.setChanged();
        BountySavedData data = data();
        data.putActive(bounty);
        data.setDirty();
        activeTargets = null;
        debug("created bounty {} sponsor={} target={} rewards={}", bounty.id(), bounty.sponsor(), target, bounty.summary());

        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(target);
        if (onlineTarget != null) {
            BountyNotifier.get().bountyPlaced(onlineTarget, bounty, nameOf(sponsor.getUUID()));
        }
        return BountyResult.ok(bounty);
    }

    // ---------------------------------------------------------------- cancellation

    /**
     * Cancels an active bounty and refunds its sponsor through a delivery.
     *
     * @param asOperator operators may cancel any bounty and ignore the combat lock
     */
    public BountyResult<Bounty> cancel(UUID actor, UUID bountyId, boolean asOperator) {
        BountySavedData data = data();
        Optional<Bounty> found = data.active(bountyId);
        if (found.isEmpty()) {
            return BountyResult.fail(findAny(bountyId).isPresent() ? BountyError.NOT_ACTIVE : BountyError.NOT_FOUND);
        }
        Bounty bounty = found.get();
        if (!asOperator && !bounty.sponsor().equals(actor)) {
            return BountyResult.fail(BountyError.NOT_OWNER);
        }
        if (!asOperator && isCombatLocked(bounty)) {
            return BountyResult.fail(BountyError.COMBAT_LOCKED);
        }

        Bounty closed = bounty.cancel(System.currentTimeMillis());
        data.removeActive(bountyId);
        data.addHistory(closed, BCConfig.historySize());
        for (ItemStack refund : bounty.rewards()) {
            data.addDelivery(new PendingDelivery(UUID.randomUUID(), bounty.sponsor(), refund.copy(), bountyId, PendingDelivery.Reason.REFUND));
        }
        data.setDirty();
        activeTargets = null;
        debug("cancelled bounty {} by {} (operator={})", bountyId, actor, asOperator);

        notifyRewardsAvailable(bounty.sponsor());
        return BountyResult.ok(closed);
    }

    /** True while the target took damage from a player eligible for this bounty within {@code cancelLockSeconds}. */
    public boolean isCombatLocked(Bounty bounty) {
        int lockTicks = BCConfig.cancelLockTicks();
        if (lockTicks <= 0) {
            return false;
        }
        long now = gameTime();
        return ledger.entries(bounty.target(), now, retentionTicks()).stream()
                .anyMatch(e -> e.gameTime() >= now - lockTicks && bounty.isEligibleHunter(e.attacker()));
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
        ledger.record(victim.getUUID(), new DamageLedger.Entry(attacker.get(), gameTime(), amount, type), retentionTicks());
    }

    /**
     * {@code LivingDeathEvent}: if a hunter is reliably responsible, every active bounty on the victim that they are
     * eligible for is claimed. A replayed event finds no active bounty left and does nothing.
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
                ledger.entries(victimId, gameTime(), retentionTicks()),
                BCConfig.attributionWindowTicks());
        Optional<UUID> hunter = KillAttributor.attribute(input);
        ledger.clear(victimId);
        String type = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?");
        if (hunter.isEmpty()) {
            debug("death of {} ({}): no reliable hunter, bounties stay active", victimId, type);
            return List.of();
        }
        debug("death of {} ({}): credited to {}", victimId, type, hunter.get());
        return claimAll(victimId, hunter.get());
    }

    /** Claims every active bounty on {@code target} that {@code hunter} is eligible for. */
    @VisibleForTesting
    public List<Bounty> claimAll(UUID target, UUID hunter) {
        BountySavedData data = data();
        List<Bounty> claimed = new ArrayList<>();
        long now = System.currentTimeMillis();
        // 1. Change state and create deliveries.
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
        // 2. Mark dirty. 3. Only then tell the hunter: the items wait in the board until they claim them.
        data.setDirty();
        activeTargets = null;
        notifyRewardsAvailable(hunter);
        return claimed;
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

    /** {@code PlayerLoggedInEvent}. */
    public void onPlayerLogin(ServerPlayer player) {
        BountySavedData data = data();
        if (data.rememberPlayer(player.getUUID(), player.getGameProfile().getName())) {
            data.setDirty();
        }
        notifyRewardsAvailable(player.getUUID());
        List<Bounty> onMe = activeOn(player.getUUID());
        if (!onMe.isEmpty()) {
            // Bounties may have been placed while they were away: they learn it on arrival.
            BountyNotifier.get().bountiesOnLogin(player, onMe);
        }
    }

    /** {@code PlayerLoggedOutEvent}. */
    public void onPlayerLogout(ServerPlayer player) {
        ledger.clear(player.getUUID());
    }

    public void tick() {
        if (gameTime() % 100 == 0) {
            ledger.purgeAll(gameTime(), retentionTicks());
        }
    }

    private static long retentionTicks() {
        return Math.max(BCConfig.attributionWindowTicks(), BCConfig.cancelLockTicks());
    }

    /** Test-only: wipes every bounty, delivery and ledger entry. */
    @VisibleForTesting
    public void resetForTests() {
        BountySavedData data = data();
        data.clearAll();
        data.setDirty();
        ledger.clearAll();
        activeTargets = null;
    }

    private static void debug(String message, Object... args) {
        if (BCConfig.debug()) {
            BloodContracts.LOGGER.info("[bounty] " + message, args);
        }
    }
}
