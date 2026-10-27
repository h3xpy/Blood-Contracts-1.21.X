package dev.bloodcontracts.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** v2 model: types, widen-only changes, cancel notice, hearts persistence, v1 migration. */
class V2ModelTest {
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final UUID SPONSOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static Bounty privateBounty(UUID... hunters) {
        return Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, List.of(new ItemStack(Items.DIAMOND)), 1L, BountyType.PRIVATE, List.of(hunters));
    }

    // ---------------------------------------------------------------- types and eligibility

    @Test
    void publicBountyAnyoneButSponsorAndTarget() {
        Bounty b = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, List.of(new ItemStack(Items.DIAMOND)), 1L);
        assertEquals(BountyType.PUBLIC, b.type());
        assertTrue(b.isEligibleHunter(A));
        assertFalse(b.isEligibleHunter(SPONSOR));
        assertFalse(b.isEligibleHunter(TARGET));
    }

    @Test
    void privateBountyOnlyListedHunters() {
        Bounty b = privateBounty(A);
        assertTrue(b.isEligibleHunter(A));
        assertFalse(b.isEligibleHunter(B));
        assertThrows(IllegalStateException.class, () -> b.claim(B, 2L));
    }

    @Test
    void privateBountyNeedsAValidList() {
        assertThrows(IllegalArgumentException.class, () -> privateBounty());
        assertThrows(IllegalArgumentException.class, () -> privateBounty(SPONSOR));
        assertThrows(IllegalArgumentException.class, () -> privateBounty(TARGET));
    }

    // ---------------------------------------------------------------- widen only

    @Test
    void huntersCanOnlyBeAdded() {
        Bounty b = privateBounty(A).withHunters(List.of(B, A, C));
        assertEquals(List.of(A, B, C), b.hunters());
        assertTrue(b.isEligibleHunter(B));
        // There is simply no operation that removes a hunter or makes a public bounty private.
        assertTrue(java.util.Arrays.stream(Bounty.class.getMethods()).noneMatch(m -> m.getName().toLowerCase().contains("remove")
                || m.getName().toLowerCase().contains("private")));
    }

    @Test
    void makingPublicWidensToEveryone() {
        Bounty b = privateBounty(A).madePublic();
        assertEquals(BountyType.PUBLIC, b.type());
        assertTrue(b.isEligibleHunter(C));
        assertThrows(IllegalStateException.class, () -> b.withHunters(List.of(C)));
    }

    @Test
    void closedBountiesCannotBeWidened() {
        Bounty closed = privateBounty(A).cancel(5L);
        assertThrows(IllegalStateException.class, () -> closed.withHunters(List.of(B)));
        assertThrows(IllegalStateException.class, closed::madePublic);
    }

    // ---------------------------------------------------------------- cancel notice

    @Test
    void noticeIsNeverShortenedAndCanBeWithdrawn() {
        Bounty b = privateBounty(A).withCancelAt(1000L);
        assertEquals(1000L, b.cancelAt());
        assertSame(b, b.withCancelAt(10L), "a second request must not shorten the notice");
        Bounty resumed = b.resumed();
        assertNull(resumed.cancelAt());
        assertTrue(resumed.isActive());
        assertNull(b.claim(A, 2L).cancelAt(), "a claimed bounty has no pending cancellation");
    }

    // ---------------------------------------------------------------- persistence v2

    @Test
    void v2FieldsSurviveRoundTrip() {
        BountySavedData data = new BountySavedData();
        Bounty b = privateBounty(A, B).withCancelAt(5000L);
        data.putActive(b);
        data.addTransfer(new HeartTransfer(UUID.randomUUID(), TARGET, SPONSOR, 2, 7L));
        data.setRegen(TARGET, new BountySavedData.RegenGauge(0.5, 99L));
        data.setImmuneUntil(TARGET, 12345L);
        data.addPairCount(A, TARGET, 3);

        CompoundTag saved = data.save(new CompoundTag(), REGISTRIES);
        assertEquals(BountySavedData.DATA_VERSION, saved.getInt("DataVersion"));
        BountySavedData loaded = BountySavedData.load(saved, REGISTRIES);

        Bounty lb = loaded.active(b.id()).orElseThrow();
        assertEquals(BountyType.PRIVATE, lb.type());
        assertEquals(List.of(A, B), lb.hunters());
        assertEquals(5000L, lb.cancelAt());
        assertEquals(1, loaded.transfers().size());
        assertEquals(2, loaded.balance(SPONSOR));
        assertEquals(-2, loaded.balance(TARGET));
        assertEquals(0.5, loaded.regen(TARGET).orElseThrow().progress());
        assertEquals(12345L, loaded.immuneUntil(TARGET).orElseThrow());
        assertEquals(3, loaded.pairCount(A, TARGET));
        assertEquals(BountySavedData.DATA_VERSION, loaded.loadedDataVersion());
    }

    @Test
    void balancesAlwaysSumToZero() {
        BountySavedData data = new BountySavedData();
        data.addTransfer(new HeartTransfer(UUID.randomUUID(), TARGET, SPONSOR, 2, 1L));
        data.addTransfer(new HeartTransfer(UUID.randomUUID(), A, SPONSOR, 1, 2L));
        data.addTransfer(new HeartTransfer(UUID.randomUUID(), SPONSOR, B, 2, 3L));
        int sum = 0;
        for (UUID p : List.of(TARGET, SPONSOR, A, B, C)) {
            sum += data.balance(p);
        }
        assertEquals(0, sum);
        assertEquals(1, data.balance(SPONSOR));
    }

    // ---------------------------------------------------------------- migration

    /** A real save written by v1 (1.0.x) during a three-player test session. */
    @Test
    void realV1SaveMigratesWithoutLosingAnything() throws Exception {
        CompoundTag root;
        try (InputStream in = V2ModelTest.class.getResourceAsStream("/v1/bloodcontracts_bounties_v1.dat")) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
        CompoundTag v1 = root.getCompound("data");
        assertFalse(v1.contains("DataVersion"), "fixture must be a v1 save");
        int v1Active = v1.getList("Active", 10).size();
        int v1History = v1.getList("History", 10).size();
        int v1Deliveries = v1.getList("Deliveries", 10).size();

        BountySavedData migrated = BountySavedData.load(v1, REGISTRIES);

        assertEquals(1, migrated.loadedDataVersion());
        assertTrue(migrated.isDirty(), "migrated data must be saved back in the v2 format");
        assertEquals(v1Active, migrated.activeBounties().size(), "active bounties lost");
        assertEquals(v1History, migrated.history().size(), "history lost");
        assertEquals(v1Deliveries, migrated.deliveries().size(), "deliveries lost");
        assertEquals(0, migrated.unreadableEntries());
        migrated.activeBounties().forEach(b -> {
            assertEquals(BountyType.PUBLIC, b.type(), "v1 bounties become PUBLIC");
            assertTrue(b.hunters().isEmpty());
            assertNull(b.cancelAt());
        });
        // Items are intact: same stacks after a v2 round trip, no duplication.
        CompoundTag v2 = migrated.save(new CompoundTag(), REGISTRIES);
        BountySavedData reloaded = BountySavedData.load(v2, REGISTRIES);
        assertEquals(2, reloaded.loadedDataVersion());
        for (Bounty b : migrated.activeBounties()) {
            Bounty r = reloaded.active(b.id()).orElseThrow();
            assertEquals(b.rewards().size(), r.rewards().size());
            for (int i = 0; i < b.rewards().size(); i++) {
                assertTrue(ItemStack.matches(b.rewards().get(i), r.rewards().get(i)));
            }
        }
        assertEquals(migrated.knownPlayers(), reloaded.knownPlayers());
    }

    /** v1 allowed several bounties per sponsor-target pair and more than 3 per sponsor: they all stay active. */
    @Test
    void v1BountiesBreakingTheNewLimitsStayActive() {
        CompoundTag v1 = new CompoundTag();
        v1.putInt("FormatVersion", 1);
        net.minecraft.nbt.ListTag active = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 5; i++) {
            Bounty b = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, List.of(new ItemStack(Items.EMERALD, i + 1)), i);
            CompoundTag tag = BountySavedData.writeBounty(b, REGISTRIES);
            tag.remove("Type"); // v1 had no type
            active.add(tag);
        }
        v1.put("Active", active);
        BountySavedData migrated = BountySavedData.load(v1, REGISTRIES);
        assertEquals(5, migrated.activeBounties().size());
    }
}
