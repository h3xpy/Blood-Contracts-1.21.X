package dev.bloodcontracts.attribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DamageLedgerTest {
    private static final UUID VICTIM = UUID.randomUUID();
    private static final UUID OTHER_VICTIM = UUID.randomUUID();
    private static final UUID HUNTER = UUID.randomUUID();

    private static DamageLedger.Entry at(long time) {
        return new DamageLedger.Entry(HUNTER, time, 1f, "minecraft:player_attack");
    }

    @Test
    void entriesAreKeptInOrderWithinRetention() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(VICTIM, at(100), 200);
        ledger.record(VICTIM, at(150), 200);
        assertEquals(List.of(at(100), at(150)), ledger.entries(VICTIM, 160, 200));
    }

    @Test
    void recordingPurgesOldEntriesOfThatVictim() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(VICTIM, at(0), 200);
        ledger.record(VICTIM, at(500), 200);
        assertEquals(List.of(at(500)), ledger.entries(VICTIM, 500, 200));
    }

    @Test
    void readingPurgesExpiredEntries() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(VICTIM, at(100), 200);
        assertEquals(List.of(at(100)), ledger.entries(VICTIM, 300, 200));
        assertTrue(ledger.entries(VICTIM, 301, 200).isEmpty());
        assertEquals(0, ledger.victimCount());
    }

    @Test
    void purgeAllDropsExpiredVictims() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(VICTIM, at(0), 200);
        ledger.record(OTHER_VICTIM, at(1000), 200);
        ledger.purgeAll(1000, 200);
        assertEquals(1, ledger.victimCount());
        assertEquals(List.of(at(1000)), ledger.entries(OTHER_VICTIM, 1000, 200));
    }

    @Test
    void clearForgetsOneVictim() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(VICTIM, at(0), 200);
        ledger.record(OTHER_VICTIM, at(0), 200);
        ledger.clear(VICTIM);
        assertTrue(ledger.entries(VICTIM, 0, 200).isEmpty());
        assertEquals(1, ledger.entries(OTHER_VICTIM, 0, 200).size());
    }
}
