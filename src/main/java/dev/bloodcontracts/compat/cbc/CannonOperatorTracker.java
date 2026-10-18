package dev.bloodcontracts.compat.cbc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

/**
 * Remembers, per cannon mount, the last player who directly operated it and when. In memory only.
 * Pure Java apart from {@link GlobalPos}: unit-testable.
 */
public final class CannonOperatorTracker {
    public record Interaction(UUID player, long gameTime) {}

    private final Map<GlobalPos, Interaction> lastInteraction = new HashMap<>();

    public void record(GlobalPos mount, UUID player, long gameTime) {
        lastInteraction.put(mount, new Interaction(player, gameTime));
    }

    /** The last operator of {@code mount} if they interacted within {@code windowTicks}. */
    public Optional<UUID> operator(GlobalPos mount, long now, long windowTicks) {
        Interaction i = lastInteraction.get(mount);
        if (i == null || i.gameTime() > now || now - i.gameTime() > windowTicks) {
            return Optional.empty();
        }
        return Optional.of(i.player());
    }

    /** Drops interactions older than {@code windowTicks}. */
    public void purge(long now, long windowTicks) {
        lastInteraction.values().removeIf(i -> now - i.gameTime() > windowTicks);
    }

    public void clear() {
        lastInteraction.clear();
    }

    public int size() {
        return lastInteraction.size();
    }
}
