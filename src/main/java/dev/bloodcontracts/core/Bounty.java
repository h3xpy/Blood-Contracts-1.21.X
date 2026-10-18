package dev.bloodcontracts.core;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable bounty. State transitions return a new instance and only {@link BountyManager} performs them.
 * <p>
 * An {@link BountyState#ACTIVE} bounty holds its reward (the escrow). A closed bounty never holds an item:
 * its reward was handed to a {@link PendingDelivery} during the same transition; only a description
 * ({@code rewardItem} x {@code rewardCount}) is kept for the history.
 *
 * @param createdAt wall-clock millis, for display
 * @param closedAt  wall-clock millis, 0 while active
 */
public record Bounty(
        UUID id,
        UUID sponsor,
        UUID target,
        ItemStack reward,
        long createdAt,
        BountyState state,
        @Nullable UUID claimedBy,
        long closedAt,
        ResourceLocation rewardItem,
        int rewardCount) {

    public Bounty {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sponsor);
        Objects.requireNonNull(target);
        Objects.requireNonNull(reward);
        Objects.requireNonNull(state);
        Objects.requireNonNull(rewardItem);
        if (state == BountyState.ACTIVE && reward.isEmpty()) {
            throw new IllegalArgumentException("An active bounty must hold a reward");
        }
        if (state != BountyState.ACTIVE && !reward.isEmpty()) {
            throw new IllegalArgumentException("A closed bounty cannot hold an item");
        }
        if (state == BountyState.CLAIMED && claimedBy == null) {
            throw new IllegalArgumentException("A claimed bounty needs a hunter");
        }
    }

    /** Creates an active bounty. The reward is copied: the caller keeps no shared reference. */
    public static Bounty create(UUID id, UUID sponsor, UUID target, ItemStack reward, long now) {
        if (sponsor.equals(target)) {
            throw new IllegalArgumentException("Sponsor and target must differ");
        }
        ItemStack copy = reward.copy();
        return new Bounty(id, sponsor, target, copy, now, BountyState.ACTIVE, null, 0L,
                BuiltInRegistries.ITEM.getKey(copy.getItem()), copy.getCount());
    }

    public boolean isActive() {
        return state == BountyState.ACTIVE;
    }

    /** A hunter can claim this bounty only if they are neither its sponsor nor its target. */
    public boolean isEligibleHunter(UUID hunter) {
        return !hunter.equals(sponsor) && !hunter.equals(target);
    }

    /** ACTIVE → CLAIMED. The returned bounty no longer holds the reward. */
    public Bounty claim(UUID hunter, long now) {
        requireActive();
        if (!isEligibleHunter(hunter)) {
            throw new IllegalStateException("Hunter " + hunter + " is not eligible for bounty " + id);
        }
        return new Bounty(id, sponsor, target, ItemStack.EMPTY, createdAt, BountyState.CLAIMED, hunter, now, rewardItem, rewardCount);
    }

    /** ACTIVE → CANCELLED. The returned bounty no longer holds the reward. */
    public Bounty cancel(long now) {
        requireActive();
        return new Bounty(id, sponsor, target, ItemStack.EMPTY, createdAt, BountyState.CANCELLED, null, now, rewardItem, rewardCount);
    }

    private void requireActive() {
        if (state != BountyState.ACTIVE) {
            throw new IllegalStateException("Bounty " + id + " is " + state + ", not ACTIVE");
        }
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
