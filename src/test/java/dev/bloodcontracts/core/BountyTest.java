package dev.bloodcontracts.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class BountyTest {
    private static final UUID SPONSOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID HUNTER = UUID.randomUUID();

    private static Bounty active() {
        return Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, new ItemStack(Items.DIAMOND, 3), 1L);
    }

    @Test
    void createCopiesTheReward() {
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        Bounty b = Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, source, 1L);
        assertNotSame(source, b.reward());
        source.setCount(1);
        assertEquals(3, b.reward().getCount());
        assertEquals(BountyState.ACTIVE, b.state());
        assertEquals(3, b.rewardCount());
        assertEquals("minecraft:diamond", b.rewardItem().toString());
    }

    @Test
    void createRejectsSelfTargetAndEmptyReward() {
        assertThrows(IllegalArgumentException.class, () -> Bounty.create(UUID.randomUUID(), SPONSOR, SPONSOR, new ItemStack(Items.DIAMOND), 1L));
        assertThrows(IllegalArgumentException.class, () -> Bounty.create(UUID.randomUUID(), SPONSOR, TARGET, ItemStack.EMPTY, 1L));
    }

    @Test
    void claimMovesToClaimedAndReleasesTheReward() {
        Bounty claimed = active().claim(HUNTER, 2L);
        assertEquals(BountyState.CLAIMED, claimed.state());
        assertEquals(HUNTER, claimed.claimedBy());
        assertTrue(claimed.reward().isEmpty());
        assertEquals(2L, claimed.closedAt());
        assertEquals(3, claimed.rewardCount());
    }

    @Test
    void eligibility() {
        Bounty b = active();
        assertTrue(b.isEligibleHunter(HUNTER));
        assertFalse(b.isEligibleHunter(SPONSOR));
        assertFalse(b.isEligibleHunter(TARGET));
        assertThrows(IllegalStateException.class, () -> b.claim(SPONSOR, 2L));
        assertThrows(IllegalStateException.class, () -> b.claim(TARGET, 2L));
    }

    @Test
    void cancelMovesToCancelled() {
        Bounty cancelled = active().cancel(5L);
        assertEquals(BountyState.CANCELLED, cancelled.state());
        assertTrue(cancelled.reward().isEmpty());
    }

    @Test
    void closedBountiesCannotTransitionAgain() {
        Bounty claimed = active().claim(HUNTER, 2L);
        Bounty cancelled = active().cancel(2L);
        assertThrows(IllegalStateException.class, () -> claimed.cancel(3L));
        assertThrows(IllegalStateException.class, () -> claimed.claim(UUID.randomUUID(), 3L));
        assertThrows(IllegalStateException.class, () -> cancelled.cancel(3L));
        assertThrows(IllegalStateException.class, () -> cancelled.claim(HUNTER, 3L));
    }

    @Test
    void invariantsAreEnforcedByTheConstructor() {
        Bounty b = active();
        assertThrows(IllegalArgumentException.class, () -> new Bounty(b.id(), SPONSOR, TARGET, ItemStack.EMPTY, 1L,
                BountyState.ACTIVE, null, 0L, b.rewardItem(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Bounty(b.id(), SPONSOR, TARGET, new ItemStack(Items.DIRT), 1L,
                BountyState.CANCELLED, null, 0L, b.rewardItem(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Bounty(b.id(), SPONSOR, TARGET, ItemStack.EMPTY, 1L,
                BountyState.CLAIMED, null, 0L, b.rewardItem(), 1));
    }
}
