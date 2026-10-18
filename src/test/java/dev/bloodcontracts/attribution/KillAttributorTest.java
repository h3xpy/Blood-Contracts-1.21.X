package dev.bloodcontracts.attribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.bloodcontracts.attribution.KillAttributor.DeathCause;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KillAttributorTest {
    private static final UUID VICTIM = UUID.randomUUID();
    private static final UUID HUNTER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final int WINDOW = 200;
    private static final long NOW = 10_000;

    private static Optional<UUID> attribute(Optional<UUID> direct, DeathCause cause, DamageLedger.Entry... entries) {
        return KillAttributor.attribute(new KillAttributor.Input(VICTIM, direct, cause, NOW, List.of(entries), WINDOW));
    }

    private static DamageLedger.Entry hit(UUID attacker, long ticksAgo) {
        return new DamageLedger.Entry(attacker, NOW - ticksAgo, 2f, "minecraft:player_attack");
    }

    @Test
    void directPlayerIsCredited() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.of(HUNTER), DeathCause.OTHER));
    }

    @Test
    void directPlayerWinsOverLedger() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.of(HUNTER), DeathCause.OTHER, hit(OTHER, 1)));
    }

    @Test
    void victimIsNeverCreditedDirectly() {
        assertEquals(Optional.empty(), attribute(Optional.of(VICTIM), DeathCause.OTHER));
    }

    @Test
    void environmentalDeathWithinWindowCreditsLastAttacker() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(HUNTER, 50)));
    }

    @Test
    void environmentalDeathAtWindowEdgeIsCredited() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(HUNTER, WINDOW)));
    }

    @Test
    void environmentalDeathOutsideWindowIsNotCredited() {
        assertEquals(Optional.empty(), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(HUNTER, WINDOW + 1)));
    }

    @Test
    void twoAttackersMostRecentWins() {
        assertEquals(Optional.of(OTHER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(OTHER, 10), hit(HUNTER, 100)));
        assertEquals(Optional.of(HUNTER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(OTHER, 100), hit(HUNTER, 10)));
    }

    @Test
    void sameTickTieGoesToLaterRecordedHit() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(OTHER, 5), hit(HUNTER, 5)));
    }

    @Test
    void environmentalWithoutLedgerIsNotCredited() {
        assertEquals(Optional.empty(), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL));
    }

    @Test
    void commandKillIsNeverCredited() {
        assertEquals(Optional.empty(), attribute(Optional.empty(), DeathCause.COMMAND, hit(HUNTER, 1)));
        assertEquals(Optional.empty(), attribute(Optional.of(HUNTER), DeathCause.COMMAND, hit(HUNTER, 1)));
    }

    @Test
    void otherCauseIgnoresLedger() {
        // e.g. killed by a zombie after a player hit: not reliable, nobody is credited.
        assertEquals(Optional.empty(), attribute(Optional.empty(), DeathCause.OTHER, hit(HUNTER, 1)));
    }

    @Test
    void selfDamageInLedgerIsIgnored() {
        assertEquals(Optional.of(HUNTER), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(HUNTER, 50), hit(VICTIM, 1)));
    }

    @Test
    void futureEntriesAreIgnored() {
        assertEquals(Optional.empty(), attribute(Optional.empty(), DeathCause.ENVIRONMENTAL, hit(HUNTER, -5)));
    }
}
