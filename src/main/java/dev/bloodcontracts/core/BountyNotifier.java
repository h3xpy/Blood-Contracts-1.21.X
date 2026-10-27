package dev.bloodcontracts.core;

import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/**
 * Out-of-chat notifications fired by {@link BountyManager}. The network layer installs the real implementation;
 * the default does nothing, so the core never depends on networking.
 */
public interface BountyNotifier {
    /** A bounty was just placed on {@code target}, who is online. */
    void bountyPlaced(ServerPlayer target, Bounty bounty, String sponsorName);

    /** {@code target} just logged in and has active bounties on their head. */
    void bountiesOnLogin(ServerPlayer target, List<Bounty> bounties);

    /** {@code player} has {@code count} stacks waiting to be claimed in the board. */
    void rewardsAvailable(ServerPlayer player, int count);

    /**
     * The target of {@code sponsor}'s bounty was killed and the bounty collected by {@code hunterName}.
     *
     * @param heartGained the sponsor received the heart taken from the target
     */
    default void targetDown(ServerPlayer sponsor, Bounty bounty, String hunterName, boolean heartGained) {}

    /** {@code player} just logged in (the client gets the settings its help tab shows). */
    default void loggedIn(ServerPlayer player) {}

    BountyNotifier NONE = new BountyNotifier() {
        @Override public void bountyPlaced(ServerPlayer target, Bounty bounty, String sponsorName) {}
        @Override public void bountiesOnLogin(ServerPlayer target, List<Bounty> bounties) {}
        @Override public void rewardsAvailable(ServerPlayer player, int count) {}
    };

    static BountyNotifier get() {
        return Holder.current;
    }

    static void set(BountyNotifier notifier) {
        Holder.current = Objects.requireNonNull(notifier);
    }

    final class Holder {
        private static volatile BountyNotifier current = NONE;

        private Holder() {}
    }
}
