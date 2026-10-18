package dev.bloodcontracts.attribution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides who is responsible for a death. Pure function of its inputs: no game state, unit-testable.
 * <ol>
 *   <li>Death caused by a command ({@code /kill}): never attributed, whatever the ledger says.</li>
 *   <li>The killing source resolves to a player (sword, arrow, TNT, pet...): that player.</li>
 *   <li>Environmental death (void, fall, lava, fire, drowning...): the player whose damage is the most recent
 *       within the attribution window. With two attackers, the most recent hit wins.</li>
 *   <li>Anything else (mob, unresolvable source): nobody.</li>
 * </ol>
 * The victim themself is never credited.
 */
public final class KillAttributor {
    public enum DeathCause { COMMAND, ENVIRONMENTAL, OTHER }

    /**
     * @param directResponsible player resolved from the killing {@code DamageSource}, if any
     * @param recentDamage      the victim's ledger entries (any order)
     */
    public record Input(UUID victim, Optional<UUID> directResponsible, DeathCause cause, long gameTime,
                        List<DamageLedger.Entry> recentDamage, int windowTicks) {}

    private KillAttributor() {}

    public static Optional<UUID> attribute(Input in) {
        if (in.cause() == DeathCause.COMMAND) {
            return Optional.empty();
        }
        if (in.directResponsible().isPresent()) {
            return in.directResponsible().filter(p -> !p.equals(in.victim()));
        }
        if (in.cause() != DeathCause.ENVIRONMENTAL) {
            return Optional.empty();
        }
        long oldest = in.gameTime() - in.windowTicks();
        DamageLedger.Entry best = null;
        for (DamageLedger.Entry e : in.recentDamage()) {
            if (e.gameTime() < oldest || e.gameTime() > in.gameTime() || e.attacker().equals(in.victim())) {
                continue;
            }
            // ">=": on a tie, the later-recorded hit wins.
            if (best == null || e.gameTime() >= best.gameTime()) {
                best = e;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.attacker());
    }
}
