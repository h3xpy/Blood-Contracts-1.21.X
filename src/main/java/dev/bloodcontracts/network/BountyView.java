package dev.bloodcontracts.network;

import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only, display-only snapshot of a bounty sent to clients. The reward stack is a copy for rendering; nothing
 * the client does with it can reach the server.
 *
 * @param sponsorName present only if the viewer may see it ({@code showSponsor}, or the viewer is the sponsor)
 * @param mine        the viewer sponsored this bounty (and may try to cancel it)
 */
public record BountyView(UUID id, String targetName, Optional<String> sponsorName, boolean mine, ItemStack reward,
                         long createdAt, BountyState state, Optional<String> hunterName, long closedAt) {

    public static final StreamCodec<RegistryFriendlyByteBuf, BountyView> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, v.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, v.targetName);
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, v.sponsorName);
                buf.writeBoolean(v.mine);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.reward);
                buf.writeLong(v.createdAt);
                buf.writeEnum(v.state);
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, v.hunterName);
                buf.writeLong(v.closedAt);
            },
            buf -> new BountyView(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                    buf.readBoolean(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readLong(),
                    buf.readEnum(BountyState.class),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                    buf.readLong()));

    public static BountyView of(Bounty b, BountyManager manager, UUID viewer, boolean viewerIsOperator) {
        boolean mine = b.sponsor().equals(viewer);
        Optional<String> sponsor = BCConfig.showSponsor() || mine || viewerIsOperator
                ? Optional.of(manager.nameOf(b.sponsor())) : Optional.empty();
        ItemStack display = b.isActive()
                ? b.reward().copy()
                : new ItemStack(BuiltInRegistries.ITEM.get(b.rewardItem()), Math.max(1, b.rewardCount()));
        Optional<String> hunter = Optional.ofNullable(b.claimedBy()).map(manager::nameOf);
        return new BountyView(b.id(), manager.nameOf(b.target()), sponsor, mine, display, b.createdAt(), b.state(), hunter, b.closedAt());
    }
}
