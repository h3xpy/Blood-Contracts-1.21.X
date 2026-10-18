package dev.bloodcontracts.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class BountySavedDataTest {
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final UUID SPONSOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID HUNTER = UUID.randomUUID();

    private static BountySavedData roundTrip(BountySavedData data) {
        CompoundTag saved = data.save(new CompoundTag(), REGISTRIES);
        return BountySavedData.load(saved, REGISTRIES);
    }

    @Test
    void activeHistoryAndDeliveriesSurviveRoundTrip() {
        BountySavedData data = new BountySavedData();
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
        named.setDamageValue(17);
        Bounty active = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, named, 42L);
        Bounty closed = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, new ItemStack(Items.EMERALD, 5), 1L).claim(HUNTER, 2L);
        PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), HUNTER, new ItemStack(Items.EMERALD, 5), closed.id(), PendingDelivery.Reason.REWARD);
        data.putActive(active);
        data.addHistory(closed, 10);
        data.addDelivery(delivery);

        BountySavedData loaded = roundTrip(data);

        Bounty loadedActive = loaded.active(active.id()).orElseThrow();
        assertEquals(active.sponsor(), loadedActive.sponsor());
        assertEquals(active.target(), loadedActive.target());
        assertEquals(active.createdAt(), loadedActive.createdAt());
        assertTrue(ItemStack.matches(named, loadedActive.rewards().get(0)), "reward item, count and components must match");

        assertEquals(1, loaded.history().size());
        Bounty loadedClosed = loaded.history().get(0);
        assertEquals(BountyState.CLAIMED, loadedClosed.state());
        assertEquals(HUNTER, loadedClosed.claimedBy());
        assertEquals(5, loadedClosed.summary().get(0).count());

        assertEquals(1, loaded.deliveries().size());
        PendingDelivery loadedDelivery = loaded.deliveries().get(0);
        assertEquals(delivery.id(), loadedDelivery.id());
        assertEquals(HUNTER, loadedDelivery.recipient());
        assertTrue(ItemStack.matches(delivery.stack(), loadedDelivery.stack()));
        assertEquals(PendingDelivery.Reason.REWARD, loadedDelivery.reason());
    }

    @Test
    void historyIsBoundedOldestFirstOut() {
        BountySavedData data = new BountySavedData();
        for (int i = 0; i < 5; i++) {
            data.addHistory(Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, new ItemStack(Items.DIRT, i + 1), i).cancel(i), 3);
        }
        List<Bounty> history = data.history();
        assertEquals(3, history.size());
        assertEquals(List.of(3, 4, 5), history.stream().map(b -> b.summary().get(0).count()).toList());
    }

    @Test
    void unknownItemDoesNotCrashAndRawDataIsKept() {
        BountySavedData data = new BountySavedData();
        Bounty active = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, new ItemStack(Items.DIAMOND), 1L);
        data.putActive(active);
        data.addDelivery(new PendingDelivery(UUID.randomUUID(), HUNTER, new ItemStack(Items.EMERALD), active.id(), PendingDelivery.Reason.REWARD));
        CompoundTag saved = data.save(new CompoundTag(), REGISTRIES);
        // Simulate a removed mod: rewrite the item ids to something that no longer exists.
        CompoundTag bountyTag = saved.getList("Active", Tag.TAG_COMPOUND).getCompound(0);
        bountyTag.getList("Rewards", Tag.TAG_COMPOUND).getCompound(0).putString("id", "removedmod:relic");
        CompoundTag deliveryTag = saved.getList("Deliveries", Tag.TAG_COMPOUND).getCompound(0);
        deliveryTag.getCompound("Item").putString("id", "removedmod:relic");

        BountySavedData loaded = BountySavedData.load(saved, REGISTRIES);

        assertTrue(loaded.activeBounties().isEmpty());
        assertTrue(loaded.deliveries().isEmpty());
        assertEquals(2, loaded.unreadableEntries());

        // Saved again, the raw entries are written back untouched, so re-adding the mod restores them.
        CompoundTag resaved = loaded.save(new CompoundTag(), REGISTRIES);
        ListTag resavedActive = resaved.getList("Active", Tag.TAG_COMPOUND);
        ListTag resavedDeliveries = resaved.getList("Deliveries", Tag.TAG_COMPOUND);
        assertEquals(bountyTag, resavedActive.getCompound(0));
        assertEquals(deliveryTag, resavedDeliveries.getCompound(0));
    }

    @Test
    void severalRewardsSurviveRoundTrip() {
        BountySavedData data = new BountySavedData();
        Bounty b = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET,
                List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 7), new ItemStack(Items.TOTEM_OF_UNDYING)), 1L);
        data.putActive(b);
        Bounty loaded = roundTrip(data).active(b.id()).orElseThrow();
        assertEquals(3, loaded.rewards().size());
        for (int i = 0; i < 3; i++) {
            assertTrue(ItemStack.matches(b.rewards().get(i), loaded.rewards().get(i)));
        }
    }

    @Test
    void oneUnknownStackQuarantinesTheWholeBounty() {
        BountySavedData data = new BountySavedData();
        Bounty b = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)), 1L);
        data.putActive(b);
        CompoundTag saved = data.save(new CompoundTag(), REGISTRIES);
        saved.getList("Active", Tag.TAG_COMPOUND).getCompound(0).getList("Rewards", Tag.TAG_COMPOUND).getCompound(1).putString("id", "gone:item");
        BountySavedData loaded = BountySavedData.load(saved, REGISTRIES);
        assertTrue(loaded.activeBounties().isEmpty(), "a partial bounty would lose an item");
        assertEquals(1, loaded.unreadableEntries());
    }

    @Test
    void legacySingleRewardFormatIsRead() {
        // Format written by 0.1.0: one "Reward" stack, history described by RewardItem/RewardCount.
        UUID id = UUID.randomUUID();
        CompoundTag active = new CompoundTag();
        active.putUUID("Id", id);
        active.putUUID("Sponsor", SPONSOR);
        active.putUUID("Target", TARGET);
        active.put("Reward", new ItemStack(Items.DIAMOND, 4).save(REGISTRIES));
        active.putLong("CreatedAt", 5L);
        active.putString("State", "ACTIVE");
        active.putLong("ClosedAt", 0L);
        active.putString("RewardItem", "minecraft:diamond");
        active.putInt("RewardCount", 4);
        CompoundTag closed = active.copy();
        closed.putUUID("Id", UUID.randomUUID());
        closed.remove("Reward");
        closed.putString("State", "CLAIMED");
        closed.putUUID("ClaimedBy", HUNTER);
        CompoundTag root = new CompoundTag();
        ListTag a = new ListTag();
        a.add(active);
        ListTag h = new ListTag();
        h.add(closed);
        root.put("Active", a);
        root.put("History", h);

        BountySavedData loaded = BountySavedData.load(root, REGISTRIES);
        Bounty b = loaded.active(id).orElseThrow();
        assertEquals(1, b.rewards().size());
        assertEquals(4, b.rewards().get(0).getCount());
        assertEquals(1, loaded.history().size());
        assertEquals(4, loaded.history().get(0).summary().get(0).count());
    }

    @Test
    void emptyTagLoadsEmptyData() {
        BountySavedData loaded = BountySavedData.load(new CompoundTag(), REGISTRIES);
        assertTrue(loaded.activeBounties().isEmpty());
        assertTrue(loaded.history().isEmpty());
        assertTrue(loaded.deliveries().isEmpty());
    }
}
