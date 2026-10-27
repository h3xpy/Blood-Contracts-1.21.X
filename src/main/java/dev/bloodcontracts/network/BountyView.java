package dev.bloodcontracts.network;

import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only, display-only snapshot of a bounty sent to clients. The reward stacks are copies for rendering; nothing
 * the client does with them can reach the server.
 *
 * @param sponsorName present only if the viewer may see it ({@code showSponsor}, the viewer is the sponsor, operator)
 * @param mine        the viewer sponsored this bounty (and may widen, cancel or resume it)
 * @param hunters     the private hunter list: present only for the sponsor and operators, never for anyone else
 * @param cancelAt    end of the cancel notice, if a cancellation is pending (server clock)
 * @param eligible    the viewer could collect this bounty by killing its target (immunity aside)
 */
public record BountyView(UUID id, UUID targetId, String targetName, Optional<String> sponsorName, boolean mine,
                         List<ItemStack> rewards, long createdAt, BountyState state, Optional<String> hunterName, long closedAt,
                         BountyType type, Optional<List<HunterRef>> hunters, Optional<Long> cancelAt, boolean eligible) {

    /** A hunter on a private list. */
    public record HunterRef(UUID id, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, HunterRef> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, HunterRef::id, ByteBufCodecs.STRING_UTF8, HunterRef::name, HunterRef::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<List<HunterRef>>> HUNTERS_CODEC =
            ByteBufCodecs.optional(HunterRef.STREAM_CODEC.apply(ByteBufCodecs.list(BCPayloads.MAX_HUNTERS_ON_WIRE)));

    public static final StreamCodec<RegistryFriendlyByteBuf, BountyView> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, v.id);
                UUIDUtil.STREAM_CODEC.encode(buf, v.targetId);
                ByteBufCodecs.STRING_UTF8.encode(buf, v.targetName);
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, v.sponsorName);
                buf.writeBoolean(v.mine);
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, v.rewards);
                buf.writeLong(v.createdAt);
                buf.writeEnum(v.state);
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, v.hunterName);
                buf.writeLong(v.closedAt);
                buf.writeEnum(v.type);
                HUNTERS_CODEC.encode(buf, v.hunters);
                ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG).encode(buf, v.cancelAt);
                buf.writeBoolean(v.eligible);
            },
            buf -> new BountyView(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                    buf.readBoolean(),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                    buf.readLong(),
                    buf.readEnum(BountyState.class),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                    buf.readLong(),
                    buf.readEnum(BountyType.class),
                    HUNTERS_CODEC.decode(buf),
                    ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG).decode(buf),
                    buf.readBoolean()));

    public static BountyView of(Bounty b, BountyManager manager, UUID viewer, boolean viewerIsOperator) {
        boolean mine = b.sponsor().equals(viewer);
        Optional<String> sponsor = BCConfig.showSponsor() || mine || viewerIsOperator
                ? Optional.of(manager.nameOf(b.sponsor())) : Optional.empty();
        Optional<String> hunter = Optional.ofNullable(b.claimedBy()).map(manager::nameOf);
        // Confidential: only the sponsor and operators ever receive a private hunter list.
        Optional<List<HunterRef>> hunters = b.type() == BountyType.PRIVATE && (mine || viewerIsOperator)
                ? Optional.of(b.hunters().stream().map(h -> new HunterRef(h, manager.nameOf(h))).toList())
                : Optional.empty();
        return new BountyView(b.id(), b.target(), manager.nameOf(b.target()), sponsor, mine, DisplayItems.lite(b.displayStacks()),
                b.createdAt(), b.state(), hunter, b.closedAt(), b.type(), hunters, Optional.ofNullable(b.cancelAt()),
                manager.canCollect(viewer, b));
    }

    /** The first stack, for icons. */
    public ItemStack icon() {
        return rewards.isEmpty() ? ItemStack.EMPTY : rewards.get(0);
    }
}
