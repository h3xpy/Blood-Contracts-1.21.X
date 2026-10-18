package dev.bloodcontracts.attribution;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory record of recent player damage taken by each bounty target. Never persisted.
 * Pure Java (no game classes) so it can be unit-tested directly.
 */
public final class DamageLedger {
    /**
     * @param attacker   the player responsible for the damage (already resolved, never the arrow or the TNT)
     * @param gameTime   server game time when the damage was applied
     * @param damageType damage type id, for logs and debugging
     */
    public record Entry(UUID attacker, long gameTime, float amount, String damageType) {}

    private final Map<UUID, Deque<Entry>> byVictim = new HashMap<>();

    /** Records a hit, then drops entries of that victim older than {@code retentionTicks}. */
    public void record(UUID victim, Entry entry, long retentionTicks) {
        Deque<Entry> entries = byVictim.computeIfAbsent(victim, k -> new ArrayDeque<>());
        entries.addLast(entry);
        purge(entries, entry.gameTime(), retentionTicks);
    }

    /** Entries of {@code victim} that are not older than {@code retentionTicks}, oldest first. */
    public List<Entry> entries(UUID victim, long now, long retentionTicks) {
        Deque<Entry> entries = byVictim.get(victim);
        if (entries == null) {
            return List.of();
        }
        purge(entries, now, retentionTicks);
        if (entries.isEmpty()) {
            byVictim.remove(victim);
            return List.of();
        }
        return List.copyOf(entries);
    }

    /** Drops every entry older than {@code retentionTicks}. */
    public void purgeAll(long now, long retentionTicks) {
        Iterator<Deque<Entry>> it = byVictim.values().iterator();
        while (it.hasNext()) {
            Deque<Entry> entries = it.next();
            purge(entries, now, retentionTicks);
            if (entries.isEmpty()) {
                it.remove();
            }
        }
    }

    public void clear(UUID victim) {
        byVictim.remove(victim);
    }

    public void clearAll() {
        byVictim.clear();
    }

    public int victimCount() {
        return byVictim.size();
    }

    private static void purge(Deque<Entry> entries, long now, long retentionTicks) {
        long oldest = now - retentionTicks;
        entries.removeIf(e -> e.gameTime() < oldest);
    }
}
