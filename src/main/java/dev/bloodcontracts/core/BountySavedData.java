package dev.bloodcontracts.core;

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
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Bounties (active and history) and pending deliveries, saved together on the Overworld so a crash can never
 * persist "bounty claimed" without "delivery owed".
 * <p>
 * Mutators are package-private: only {@link BountyManager} changes this data.
 * <p>
 * An entry whose item cannot be decoded (its mod was removed) is logged and kept as raw NBT. It is written back
 * untouched on save and decoded again on the next load, so re-adding the mod restores it.
 */
public class BountySavedData extends SavedData {
    public static final String NAME = BloodContracts.MODID + "_bounties";
    public static final Factory<BountySavedData> FACTORY = new Factory<>(BountySavedData::new, BountySavedData::load);
    private static final int FORMAT_VERSION = 1;

    private final Map<UUID, Bounty> active = new LinkedHashMap<>();
    /** Oldest first. */
    private final Deque<Bounty> history = new ArrayDeque<>();
    private final List<PendingDelivery> deliveries = new ArrayList<>();
    private final List<CompoundTag> unreadableBounties = new ArrayList<>();
    private final List<CompoundTag> unreadableDeliveries = new ArrayList<>();
    /** Every player who ever joined (UUID → last known name): valid bounty targets even while offline. */
    private final Map<UUID, String> knownPlayers = new LinkedHashMap<>();

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

    /** @return true if this changed the data */
    boolean rememberPlayer(UUID player, String name) {
        return !name.equals(knownPlayers.put(player, name));
    }

    public int unreadableEntries() {
        return unreadableBounties.size() + unreadableDeliveries.size();
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

    void clearAll() {
        active.clear();
        history.clear();
        deliveries.clear();
        unreadableBounties.clear();
        unreadableDeliveries.clear();
        knownPlayers.clear();
    }

    // ---------------------------------------------------------------- serialization

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
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
        return tag;
    }

    public static BountySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BountySavedData data = new BountySavedData();
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
        return tag;
    }

    /**
     * Reads a bounty; also accepts the 0.1 single-reward format ({@code Reward}, {@code RewardItem}, {@code RewardCount}).
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
            return new Bounty(
                    tag.getUUID("Id"),
                    tag.getUUID("Sponsor"),
                    tag.getUUID("Target"),
                    rewards,
                    tag.getLong("CreatedAt"),
                    state,
                    tag.hasUUID("ClaimedBy") ? tag.getUUID("ClaimedBy") : null,
                    tag.getLong("ClosedAt"),
                    summary);
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
