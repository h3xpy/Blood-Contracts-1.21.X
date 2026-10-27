package dev.bloodcontracts.network;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One wanted poster: every active bounty on a target, grouped.
 *
 * @param profile       the target's profile with its "textures" property (recorded at their last login), so the client
 *                      can draw their real skin even when they are offline
 * @param immuneUntil   end of the target's immunity (server clock), if running
 * @param bounties      the active bounties on the target, oldest first
 * @param repeatHunters hunters who collected many bounties on this target (information only)
 */
public record TargetView(GameProfile profile, Optional<Long> immuneUntil, List<BountyView> bounties, List<RepeatView> repeatHunters) {

    /** "{@code hunterName} collected {@code count} bounties on {@code target}". */
    public record RepeatView(UUID target, String hunterName, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RepeatView> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RepeatView::target,
                ByteBufCodecs.STRING_UTF8, RepeatView::hunterName,
                ByteBufCodecs.VAR_INT, RepeatView::count,
                RepeatView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.GAME_PROFILE, TargetView::profile,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG), TargetView::immuneUntil,
            BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()), TargetView::bounties,
            RepeatView.STREAM_CODEC.apply(ByteBufCodecs.list()), TargetView::repeatHunters,
            TargetView::new);

    public UUID targetId() {
        return profile.getId();
    }

    public String targetName() {
        return profile.getName();
    }

    /** The viewer could collect at least one of these bounties. */
    public boolean eligible() {
        return bounties.stream().anyMatch(BountyView::eligible);
    }

    /** Creation time of the newest bounty, for sorting. */
    public long newest() {
        return bounties.stream().mapToLong(BountyView::createdAt).max().orElse(0L);
    }
}
