package dev.bloodcontracts.core;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable bounty. State transitions return a new instance and only {@link BountyManager} performs them.
 * <p>
 * An {@link BountyState#ACTIVE} bounty holds its rewards (the escrow): one or more stacks, up to
 * {@link #MAX_REWARD_STACKS}. A closed bounty never holds an item: its rewards were handed to
 * {@link PendingDelivery deliveries} during the same transition; only a description ({@link #summary()}) is kept for the
 * history.
 *
 * @param createdAt wall-clock millis, for display
 * @param closedAt  wall-clock millis, 0 while active
 */
public record Bounty(
        UUID id,
        UUID sponsor,
        UUID target,
        List<ItemStack> rewards,
        long createdAt,
        BountyState state,
        @Nullable UUID claimedBy,
        long closedAt,
        List<RewardSummary> summary) {

    /** Different stacks a single bounty can offer (the creation menu's expanded reward grid). */
    public static final int MAX_REWARD_STACKS = 9;

    /** What a reward was, kept in the history once the items themselves are delivered. */
    public record RewardSummary(ResourceLocation item, int count) {
        public static RewardSummary of(ItemStack stack) {
            return new RewardSummary(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
        }
    }

    public Bounty {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sponsor);
        Objects.requireNonNull(target);
        Objects.requireNonNull(state);
        rewards = List.copyOf(rewards);
        summary = List.copyOf(summary);
        if (state == BountyState.ACTIVE) {
            if (rewards.isEmpty() || rewards.size() > MAX_REWARD_STACKS || rewards.stream().anyMatch(ItemStack::isEmpty)) {
                throw new IllegalArgumentException("An active bounty must hold 1 to " + MAX_REWARD_STACKS + " non-empty stacks");
            }
        } else if (!rewards.isEmpty()) {
            throw new IllegalArgumentException("A closed bounty cannot hold an item");
        }
        if (state == BountyState.CLAIMED && claimedBy == null) {
            throw new IllegalArgumentException("A claimed bounty needs a hunter");
        }
    }

    /** Creates an active bounty. The stacks are copied: the caller keeps no shared reference. */
    public static Bounty create(UUID id, UUID sponsor, UUID target, List<ItemStack> rewards, long now) {
        if (sponsor.equals(target)) {
            throw new IllegalArgumentException("Sponsor and target must differ");
        }
        List<ItemStack> copies = rewards.stream().map(ItemStack::copy).toList();
        return new Bounty(id, sponsor, target, copies, now, BountyState.ACTIVE, null, 0L,
                copies.stream().map(RewardSummary::of).toList());
    }

    public static Bounty create(UUID id, UUID sponsor, UUID target, ItemStack reward, long now) {
        return create(id, sponsor, target, List.of(reward), now);
    }

    public boolean isActive() {
        return state == BountyState.ACTIVE;
    }

    /** A hunter can claim this bounty only if they are neither its sponsor nor its target. */
    public boolean isEligibleHunter(UUID hunter) {
        return !hunter.equals(sponsor) && !hunter.equals(target);
    }

    /** ACTIVE → CLAIMED. The returned bounty no longer holds the rewards. */
    public Bounty claim(UUID hunter, long now) {
        requireActive();
        if (!isEligibleHunter(hunter)) {
            throw new IllegalStateException("Hunter " + hunter + " is not eligible for bounty " + id);
        }
        return new Bounty(id, sponsor, target, List.of(), createdAt, BountyState.CLAIMED, hunter, now, summary);
    }

    /** ACTIVE → CANCELLED. The returned bounty no longer holds the rewards. */
    public Bounty cancel(long now) {
        requireActive();
        return new Bounty(id, sponsor, target, List.of(), createdAt, BountyState.CANCELLED, null, now, summary);
    }

    private void requireActive() {
        if (state != BountyState.ACTIVE) {
            throw new IllegalStateException("Bounty " + id + " is " + state + ", not ACTIVE");
        }
    }

    /** Copies of the escrowed stacks (active) or display stacks rebuilt from the summary (closed). */
    public List<ItemStack> displayStacks() {
        if (isActive()) {
            return rewards.stream().map(ItemStack::copy).toList();
        }
        return summary.stream()
                .map(s -> new ItemStack(BuiltInRegistries.ITEM.get(s.item()), Math.max(1, s.count())))
                .toList();
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
