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

    /**
     * The single operator of all fresh interactions within {@code reach} blocks (Chebyshev) of {@code center}, or
     * nothing if there is none or they disagree.
     */
    public Optional<UUID> operatorNear(GlobalPos center, int reach, long now, long windowTicks) {
        UUID found = null;
        for (Map.Entry<GlobalPos, Interaction> e : lastInteraction.entrySet()) {
            GlobalPos pos = e.getKey();
            Interaction i = e.getValue();
            if (!pos.dimension().equals(center.dimension()) || chebyshev(pos.pos(), center.pos()) > reach
                    || i.gameTime() > now || now - i.gameTime() > windowTicks) {
                continue;
            }
            if (found != null && !found.equals(i.player())) {
                return Optional.empty();
            }
            found = i.player();
        }
        return Optional.ofNullable(found);
    }

    private static int chebyshev(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
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
