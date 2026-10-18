package dev.bloodcontracts.compat.cbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CannonOperatorTrackerTest {
    private static final GlobalPos MOUNT = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 10));
    private static final GlobalPos OTHER_MOUNT = GlobalPos.of(Level.OVERWORLD, new BlockPos(20, 64, 10));
    private static final GlobalPos NETHER_MOUNT = GlobalPos.of(Level.NETHER, new BlockPos(10, 64, 10));
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void recentInteractionIsTheOperator() {
        CannonOperatorTracker t = new CannonOperatorTracker();
        t.record(MOUNT, A, 100);
        assertEquals(Optional.of(A), t.operator(MOUNT, 150, 200));
        assertEquals(Optional.of(A), t.operator(MOUNT, 300, 200));
    }

    @Test
    void staleInteractionIsNobody() {
        CannonOperatorTracker t = new CannonOperatorTracker();
        t.record(MOUNT, A, 100);
        assertEquals(Optional.empty(), t.operator(MOUNT, 301, 200));
    }

    @Test
    void lastInteractionWins() {
        CannonOperatorTracker t = new CannonOperatorTracker();
        t.record(MOUNT, A, 100);
        t.record(MOUNT, B, 120);
        assertEquals(Optional.of(B), t.operator(MOUNT, 130, 200));
    }

    @Test
    void mountsAreIndependentAcrossPositionsAndDimensions() {
        CannonOperatorTracker t = new CannonOperatorTracker();
        t.record(MOUNT, A, 100);
        assertEquals(Optional.empty(), t.operator(OTHER_MOUNT, 100, 200));
        assertEquals(Optional.empty(), t.operator(NETHER_MOUNT, 100, 200));
    }

    @Test
    void futureInteractionIsIgnoredAndPurgeDropsStale() {
        CannonOperatorTracker t = new CannonOperatorTracker();
        t.record(MOUNT, A, 500);
        assertEquals(Optional.empty(), t.operator(MOUNT, 100, 200));
        t.record(OTHER_MOUNT, B, 0);
        t.purge(500, 200);
        assertEquals(1, t.size());
    }
}
