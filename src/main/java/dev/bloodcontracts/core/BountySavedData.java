package dev.bloodcontracts.core;

import com.mojang.authlib.properties.Property;
import dev.bloodcontracts.BloodContracts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Blood Contracts persists, saved together on the Overworld so a crash can never persist half of a change
 * (e.g. "bounty claimed" without "delivery owed", or a heart transfer without the immunity that came with it):
 * bounties (active and history), pending deliveries, heart transfers, regeneration gauges, immunities, pair counters,
 * known players and their skins.
 * <p>
 * Mutators are package-private: only {@link BountyManager} changes this data.
 * <p>
 * An entry whose item cannot be decoded (its mod was removed) is logged and kept as raw NBT. It is written back
 * untouched on save and decoded again on the next load, so re-adding the mod restores it.
 */
public class BountySavedData extends SavedData {
    public static final String NAME = BloodContracts.MODID + "_bounties";
    public static final Factory<BountySavedData> FACTORY = new Factory<>(BountySavedData::new, BountySavedData::load);

    /**
     * 1: v1 (1.0.x, written as "FormatVersion" = 1, or nothing in 0.1): bounties are implicitly public, no hearts.
     * 2: v2: bounty type, hunters and cancel notice; heart transfers, regeneration gauges, immunities, pair counters.
     */
    public static final int DATA_VERSION = 2;

    /** Regeneration gauge of a target that has health out on transfers (see {@code BountyManager}). */
    public record RegenGauge(double progress, long lastUpdate) {}

    /** How many bounties a hunter collected on a target; survives the history purge. */
    public record PairKey(UUID hunter, UUID target) {}

    private final Map<UUID, Bounty> active = new LinkedHashMap<>();
    /** Oldest first. */
    private final Deque<Bounty> history = new ArrayDeque<>();
    private final List<PendingDelivery> deliveries = new ArrayList<>();
    private final List<CompoundTag> unreadableBounties = new ArrayList<>();
    private final List<CompoundTag> unreadableDeliveries = new ArrayList<>();
    /** Every player who ever joined (UUID → last known name): valid bounty targets even while offline. */
    private final Map<UUID, String> knownPlayers = new LinkedHashMap<>();
    /** Last signed skin ("textures" profile property) of each known player, so offline players keep their face. */
    private final Map<UUID, Property> skins = new LinkedHashMap<>();
    /** Source of truth for hearts, oldest first. Balances are computed from it, never stored. */
    private final List<HeartTransfer> transfers = new ArrayList<>();
    private final Map<UUID, RegenGauge> regen = new LinkedHashMap<>();
    /** Target → end of immunity (wall-clock millis). */
    private final Map<UUID, Long> immuneUntil = new LinkedHashMap<>();
    private final Map<PairKey, Integer> pairCounts = new LinkedHashMap<>();
    private int loadedDataVersion = DATA_VERSION;

    // ---------------------------------------------------------------- reads

    public Collection<Bounty> activeBounties() {
        return Collections.unmodifiableCollection(active.values());
    }

    public Optional<Bounty> active(UUID id) {
        return Optional.ofNullable(active.get(id));
    }

    /** Oldest first. */
    public List<Bounty> history() {
        return List.copyOf(history);
    }

    public List<PendingDelivery> deliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    public Optional<String> knownName(UUID player) {
        return Optional.ofNullable(knownPlayers.get(player));
    }

    public Map<UUID, String> knownPlayers() {
        return Collections.unmodifiableMap(knownPlayers);
    }

    public Optional<Property> skin(UUID player) {
        return Optional.ofNullable(skins.get(player));
    }

    public List<HeartTransfer> transfers() {
        return Collections.unmodifiableList(transfers);
    }

    /** Net balance in HP: received minus lost. */
    public int balance(UUID player) {
        int hp = 0;
        for (HeartTransfer t : transfers) {
            if (t.to().equals(player)) {
                hp += t.remainingHp();
            }
            if (t.from().equals(player)) {
                hp -= t.remainingHp();
            }
        }
        return hp;
    }

    public Optional<RegenGauge> regen(UUID target) {
        return Optional.ofNullable(regen.get(target));
    }

    public Map<UUID, RegenGauge> regenGauges() {
        return Collections.unmodifiableMap(regen);
    }

    public Optional<Long> immuneUntil(UUID target) {
        return Optional.ofNullable(immuneUntil.get(target));
    }

    public int pairCount(UUID hunter, UUID target) {
        return pairCounts.getOrDefault(new PairKey(hunter, target), 0);
    }

    public Map<PairKey, Integer> pairCounts() {
        return Collections.unmodifiableMap(pairCounts);
    }

    /** The data version this data was loaded from (1 for a v1 save, before migration). */
    public int loadedDataVersion() {
        return loadedDataVersion;
    }

    public int unreadableEntries() {
        return unreadableBounties.size() + unreadableDeliveries.size();
    }

    // ---------------------------------------------------------------- mutations (BountyManager only)

    /** @return true if this changed the data */
    boolean rememberSkin(UUID player, Property textures) {
        return !textures.equals(skins.put(player, textures));
    }

    /** @return true if this changed the data */
    boolean rememberPlayer(UUID player, String name) {
        return !name.equals(knownPlayers.put(player, name));
    }

    void putActive(Bounty bounty) {
        if (!bounty.isActive()) {
            throw new IllegalArgumentException("Not active: " + bounty.id());
        }
        active.put(bounty.id(), bounty);
    }

    void removeActive(UUID id) {
        active.remove(id);
    }

    void addHistory(Bounty closed, int maxSize) {
        history.addLast(closed);
        while (history.size() > Math.max(0, maxSize)) {
            history.removeFirst();
        }
    }

    void addDelivery(PendingDelivery delivery) {
        deliveries.add(delivery);
    }

    boolean removeDelivery(PendingDelivery delivery) {
        return deliveries.remove(delivery);
    }

    void addTransfer(HeartTransfer transfer) {
        transfers.add(transfer);
    }

    /** Replaces {@code old} by {@code updated} at the same position, or removes it if {@code updated} is null. */
    void replaceTransfer(HeartTransfer old, @Nullable HeartTransfer updated) {
        int i = transfers.indexOf(old);
        if (i < 0) {
            return;
        }
        if (updated == null) {
            transfers.remove(i);
        } else {
            transfers.set(i, updated);
        }
    }

    void setRegen(UUID target, @Nullable RegenGauge gauge) {
        if (gauge == null) {
            regen.remove(target);
        } else {
            regen.put(target, gauge);
        }
    }

    void setImmuneUntil(UUID target, long until) {
        immuneUntil.put(target, until);
    }

    /** Removes {@code target}'s immunity; true if there was one. */
    boolean clearImmunity(UUID target) {
        return immuneUntil.remove(target) != null;
    }

    void purgeImmunities(long now) {
        immuneUntil.values().removeIf(until -> until <= now);
    }

    void addPairCount(UUID hunter, UUID target, int n) {
        pairCounts.merge(new PairKey(hunter, target), n, Integer::sum);
    }

    void clearAll() {
        active.clear();
        history.clear();
        deliveries.clear();
        unreadableBounties.clear();
        unreadableDeliveries.clear();
        knownPlayers.clear();
        skins.clear();
        transfers.clear();
        regen.clear();
        immuneUntil.clear();
        pairCounts.clear();
    }

    // ---------------------------------------------------------------- serialization

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag activeList = new ListTag();
        active.values().forEach(b -> activeList.add(writeBounty(b, registries)));
        unreadableBounties.forEach(raw -> activeList.add(raw.copy()));
        tag.put("Active", activeList);

        ListTag historyList = new ListTag();
        history.forEach(b -> historyList.add(writeBounty(b, registries)));
        tag.put("History", historyList);

        ListTag deliveryList = new ListTag();
        deliveries.forEach(d -> deliveryList.add(writeDelivery(d, registries)));
        unreadableDeliveries.forEach(raw -> deliveryList.add(raw.copy()));
        tag.put("Deliveries", deliveryList);

        ListTag known = new ListTag();
        knownPlayers.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", name);
            known.add(entry);
        });
        tag.put("KnownPlayers", known);

        ListTag skinList = new ListTag();
        skins.forEach((id, p) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Value", p.value());
            if (p.signature() != null) {
                entry.putString("Signature", p.signature());
            }
            skinList.add(entry);
        });
        tag.put("Skins", skinList);

        ListTag transferList = new ListTag();
        for (HeartTransfer t : transfers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", t.id());
            entry.putUUID("From", t.from());
            entry.putUUID("To", t.to());
            entry.putInt("RemainingHp", t.remainingHp());
            entry.putLong("CreatedAt", t.createdAt());
            transferList.add(entry);
        }
        tag.put("HeartTransfers", transferList);

        ListTag regenList = new ListTag();
        regen.forEach((id, g) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", id);
            entry.putDouble("Progress", g.progress());
            entry.putLong("LastUpdate", g.lastUpdate());
            regenList.add(entry);
        });
        tag.put("Regen", regenList);

        ListTag immunityList = new ListTag();
        immuneUntil.forEach((id, until) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", id);
            entry.putLong("Until", until);
            immunityList.add(entry);
        });
        tag.put("Immunities", immunityList);

        ListTag pairList = new ListTag();
        pairCounts.forEach((k, n) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Hunter", k.hunter());
            entry.putUUID("Target", k.target());
            entry.putInt("Count", n);
            pairList.add(entry);
        });
        tag.put("PairCounts", pairList);
        return tag;
    }

    public static BountySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BountySavedData data = new BountySavedData();
        data.loadedDataVersion = tag.contains("DataVersion") ? tag.getInt("DataVersion") : 1;
        for (Tag t : tag.getList("Active", Tag.TAG_COMPOUND)) {
            CompoundTag raw = (CompoundTag) t;
            Bounty b = readBounty(raw, registries);
            if (b != null && b.isActive()) {
                data.active.put(b.id(), b);
            } else {
                BloodContracts.LOGGER.error("Blood Contracts: could not decode active bounty {}; keeping its raw data untouched", raw);
                data.unreadableBounties.add(raw.copy());
            }
        }
        for (Tag t : tag.getList("History", Tag.TAG_COMPOUND)) {
            Bounty b = readBounty((CompoundTag) t, registries);
            if (b != null && !b.isActive()) {
                data.history.addLast(b);
            }
        }
        for (Tag t : tag.getList("Deliveries", Tag.TAG_COMPOUND)) {
            CompoundTag raw = (CompoundTag) t;
            PendingDelivery d = readDelivery(raw, registries);
            if (d != null) {
                data.deliveries.add(d);
            } else {
                BloodContracts.LOGGER.error("Blood Contracts: could not decode pending delivery {}; keeping its raw data untouched", raw);
                data.unreadableDeliveries.add(raw.copy());
            }
        }
        for (Tag t : tag.getList("KnownPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (entry.hasUUID("Id")) {
                data.knownPlayers.put(entry.getUUID("Id"), entry.getString("Name"));
            }
        }
        for (Tag t : tag.getList("Skins", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (entry.hasUUID("Id") && !entry.getString("Value").isEmpty()) {
                data.skins.put(entry.getUUID("Id"), new Property("textures", entry.getString("Value"),
                        entry.contains("Signature") ? entry.getString("Signature") : null));
            }
        }
        for (Tag t : tag.getList("HeartTransfers", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            try {
                data.transfers.add(new HeartTransfer(e.getUUID("Id"), e.getUUID("From"), e.getUUID("To"),
                        e.getInt("RemainingHp"), e.getLong("CreatedAt")));
            } catch (RuntimeException ex) {
                BloodContracts.LOGGER.error("Blood Contracts: dropping malformed heart transfer {}", e, ex);
            }
        }
        for (Tag t : tag.getList("Regen", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.hasUUID("Target")) {
                data.regen.put(e.getUUID("Target"), new RegenGauge(e.getDouble("Progress"), e.getLong("LastUpdate")));
            }
        }
        for (Tag t : tag.getList("Immunities", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.hasUUID("Target")) {
                data.immuneUntil.put(e.getUUID("Target"), e.getLong("Until"));
            }
        }
        for (Tag t : tag.getList("PairCounts", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.hasUUID("Hunter") && e.hasUUID("Target")) {
                data.pairCounts.put(new PairKey(e.getUUID("Hunter"), e.getUUID("Target")), e.getInt("Count"));
            }
        }
        if (data.loadedDataVersion < DATA_VERSION) {
            // v1 -> v2: bounties without a type are read as PUBLIC (see readBounty); nothing else to convert.
            BloodContracts.LOGGER.info("Blood Contracts: migrated bounty data v{} -> v{} ({} active bounties kept as PUBLIC, {} pending deliveries)",
                    data.loadedDataVersion, DATA_VERSION, data.active.size(), data.deliveries.size());
            data.setDirty();
        }
        return data;
    }

    static CompoundTag writeBounty(Bounty b, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", b.id());
        tag.putUUID("Sponsor", b.sponsor());
        tag.putUUID("Target", b.target());
        if (!b.rewards().isEmpty()) {
            ListTag rewards = new ListTag();
            b.rewards().forEach(stack -> rewards.add(stack.save(registries)));
            tag.put("Rewards", rewards);
        }
        tag.putLong("CreatedAt", b.createdAt());
        tag.putString("State", b.state().name());
        if (b.claimedBy() != null) {
            tag.putUUID("ClaimedBy", b.claimedBy());
        }
        tag.putLong("ClosedAt", b.closedAt());
        ListTag summary = new ListTag();
        for (Bounty.RewardSummary s : b.summary()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Item", s.item().toString());
            entry.putInt("Count", s.count());
            summary.add(entry);
        }
        tag.put("Summary", summary);
        tag.putString("Type", b.type().name());
        if (!b.hunters().isEmpty()) {
            ListTag hunters = new ListTag();
            b.hunters().forEach(h -> hunters.add(NbtUtils.createUUID(h)));
            tag.put("Hunters", hunters);
        }
        if (b.cancelAt() != null) {
            tag.putLong("CancelAt", b.cancelAt());
        }
        return tag;
    }

    /**
     * Reads a bounty in any format: v2, v1 (no "Type": public), and 0.1 (single "Reward", "RewardItem"/"RewardCount").
     * Returns null if any reward stack cannot be decoded: the caller keeps the raw tag.
     */
    static @Nullable Bounty readBounty(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            BountyState state = BountyState.valueOf(tag.getString("State"));
            List<ItemStack> rewards = new ArrayList<>();
            if (state == BountyState.ACTIVE) {
                List<CompoundTag> raw = new ArrayList<>();
                if (tag.contains("Rewards", Tag.TAG_LIST)) {
                    tag.getList("Rewards", Tag.TAG_COMPOUND).forEach(t -> raw.add((CompoundTag) t));
                } else if (tag.contains("Reward", Tag.TAG_COMPOUND)) {
                    raw.add(tag.getCompound("Reward"));
                }
                for (CompoundTag stackTag : raw) {
                    Optional<ItemStack> parsed = ItemStack.parse(registries, stackTag);
                    if (parsed.isEmpty() || parsed.get().isEmpty()) {
                        return null;
                    }
                    rewards.add(parsed.get());
                }
                if (rewards.isEmpty()) {
                    return null;
                }
            }
            List<Bounty.RewardSummary> summary = new ArrayList<>();
            if (tag.contains("Summary", Tag.TAG_LIST)) {
                for (Tag t : tag.getList("Summary", Tag.TAG_COMPOUND)) {
                    CompoundTag entry = (CompoundTag) t;
                    ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
                    summary.add(new Bounty.RewardSummary(item != null ? item : ResourceLocation.withDefaultNamespace("air"), entry.getInt("Count")));
                }
            } else if (tag.contains("RewardItem")) {
                ResourceLocation item = ResourceLocation.tryParse(tag.getString("RewardItem"));
                summary.add(new Bounty.RewardSummary(item != null ? item : ResourceLocation.withDefaultNamespace("air"), tag.getInt("RewardCount")));
            } else {
                rewards.forEach(r -> summary.add(Bounty.RewardSummary.of(r)));
            }
            BountyType type = tag.contains("Type") ? BountyType.valueOf(tag.getString("Type")) : BountyType.PUBLIC;
            List<UUID> hunters = new ArrayList<>();
            for (Tag t : tag.getList("Hunters", Tag.TAG_INT_ARRAY)) {
                hunters.add(NbtUtils.loadUUID(t));
            }
            return new Bounty(
                    tag.getUUID("Id"),
                    tag.getUUID("Sponsor"),
                    tag.getUUID("Target"),
                    rewards,
                    tag.getLong("CreatedAt"),
                    state,
                    tag.hasUUID("ClaimedBy") ? tag.getUUID("ClaimedBy") : null,
                    tag.getLong("ClosedAt"),
                    summary,
                    type,
                    type == BountyType.PUBLIC ? List.of() : hunters,
                    state == BountyState.ACTIVE && tag.contains("CancelAt") ? tag.getLong("CancelAt") : null);
        } catch (RuntimeException e) {
            BloodContracts.LOGGER.error("Blood Contracts: malformed bounty entry {}", tag, e);
            return null;
        }
    }

    static CompoundTag writeDelivery(PendingDelivery d, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", d.id());
        tag.putUUID("Recipient", d.recipient());
        tag.put("Item", d.stack().save(registries));
        tag.putUUID("Bounty", d.bountyId());
        tag.putString("Reason", d.reason().name());
        return tag;
    }

    static @Nullable PendingDelivery readDelivery(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            Optional<ItemStack> stack = ItemStack.parse(registries, tag.getCompound("Item"));
            if (stack.isEmpty() || stack.get().isEmpty()) {
                return null;
            }
            return new PendingDelivery(tag.getUUID("Id"), tag.getUUID("Recipient"), stack.get(), tag.getUUID("Bounty"),
                    PendingDelivery.Reason.valueOf(tag.getString("Reason")));
        } catch (RuntimeException e) {
            BloodContracts.LOGGER.error("Blood Contracts: malformed delivery entry {}", tag, e);
            return null;
        }
    }
}
