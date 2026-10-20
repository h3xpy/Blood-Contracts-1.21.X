package dev.bloodcontracts.network;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.PendingDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Network payloads. Client → server payloads are intentions only: they carry ids, never items or rewards.
 */
public final class BCPayloads {
    private BCPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, path));
    }

    // ---------------------------------------------------------------- client → server

    /** Confirm the open creation menu: the rewards are whatever the server-side slots hold. */
    public record CreateBountyRequest(UUID target) implements CustomPacketPayload {
        public static final Type<CreateBountyRequest> TYPE = BCPayloads.payloadType("create_bounty");
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateBountyRequest> STREAM_CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, CreateBountyRequest::target, CreateBountyRequest::new);

        @Override
        public Type<CreateBountyRequest> type() {
            return TYPE;
        }
    }

    public record CancelBountyRequest(UUID bountyId) implements CustomPacketPayload {
        public static final Type<CancelBountyRequest> TYPE = BCPayloads.payloadType("cancel_bounty");
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelBountyRequest> STREAM_CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, CancelBountyRequest::bountyId, CancelBountyRequest::new);

        @Override
        public Type<CancelBountyRequest> type() {
            return TYPE;
        }
    }

    /** Claim one waiting reward, or all of them when {@code deliveryId} is empty. */
    public record ClaimRewardRequest(Optional<UUID> deliveryId) implements CustomPacketPayload {
        public static final Type<ClaimRewardRequest> TYPE = BCPayloads.payloadType("claim_reward");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardRequest> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ClaimRewardRequest::deliveryId, ClaimRewardRequest::new);

        @Override
        public Type<ClaimRewardRequest> type() {
            return TYPE;
        }
    }

    public record RequestBountyList() implements CustomPacketPayload {
        public static final RequestBountyList INSTANCE = new RequestBountyList();
        public static final Type<RequestBountyList> TYPE = BCPayloads.payloadType("request_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestBountyList> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<RequestBountyList> type() {
            return TYPE;
        }
    }

    public record OpenCreateMenuRequest() implements CustomPacketPayload {
        public static final OpenCreateMenuRequest INSTANCE = new OpenCreateMenuRequest();
        public static final Type<OpenCreateMenuRequest> TYPE = BCPayloads.payloadType("open_create_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreateMenuRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OpenCreateMenuRequest> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- server → client

    /** A stack waiting for its owner in the board's "to claim" tab. */
    public record DeliveryView(UUID id, ItemStack stack, PendingDelivery.Reason reason, String targetName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DeliveryView> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, DeliveryView::id,
                ItemStack.STREAM_CODEC, DeliveryView::stack,
                NeoForgeStreamCodecs.enumCodec(PendingDelivery.Reason.class), DeliveryView::reason,
                ByteBufCodecs.STRING_UTF8, DeliveryView::targetName,
                DeliveryView::new);
    }

    /**
     * Bounty board contents.
     *
     * @param openScreen open the board screen if it is not open yet
     * @param status     feedback for the last request (success or refusal), if any
     */
    public record BountyListPayload(List<BountyView> active, List<BountyView> history, List<DeliveryView> claimable,
                                    boolean openScreen, Optional<Component> status, boolean statusIsError) implements CustomPacketPayload {
        public static final Type<BountyListPayload> TYPE = BCPayloads.payloadType("bounty_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyListPayload> STREAM_CODEC = StreamCodec.composite(
                BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()), BountyListPayload::active,
                BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()), BountyListPayload::history,
                DeliveryView.STREAM_CODEC.apply(ByteBufCodecs.list()), BountyListPayload::claimable,
                ByteBufCodecs.BOOL, BountyListPayload::openScreen,
                ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC), BountyListPayload::status,
                ByteBufCodecs.BOOL, BountyListPayload::statusIsError,
                BountyListPayload::new);

        @Override
        public Type<BountyListPayload> type() {
            return TYPE;
        }
    }

    /** Every player the server knows (online or not), for the creation screen's target picker. */
    public record KnownPlayersPayload(List<KnownPlayer> players) implements CustomPacketPayload {
        /**
         * @param skin       signed "textures" property value recorded at the player's last login, so the client can show
         *                   their real face while they are offline
         * @param skinSignature its signature
         */
        public record KnownPlayer(UUID id, String name, boolean online, Optional<String> skin, Optional<String> skinSignature) {
            public static final StreamCodec<RegistryFriendlyByteBuf, KnownPlayer> STREAM_CODEC = StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, KnownPlayer::id,
                    ByteBufCodecs.STRING_UTF8, KnownPlayer::name,
                    ByteBufCodecs.BOOL, KnownPlayer::online,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), KnownPlayer::skin,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), KnownPlayer::skinSignature,
                    KnownPlayer::new);

            public KnownPlayer(UUID id, String name, boolean online) {
                this(id, name, online, Optional.empty(), Optional.empty());
            }
        }

        public static final Type<KnownPlayersPayload> TYPE = BCPayloads.payloadType("known_players");
        public static final StreamCodec<RegistryFriendlyByteBuf, KnownPlayersPayload> STREAM_CODEC = StreamCodec.composite(
                KnownPlayer.STREAM_CODEC.apply(ByteBufCodecs.list()), KnownPlayersPayload::players, KnownPlayersPayload::new);

        @Override
        public Type<KnownPlayersPayload> type() {
            return TYPE;
        }
    }

    /**
     * "There is a price on your head": full-screen alert on the target's client.
     *
     * @param sponsorName who placed it, if {@code alertRevealsSponsor}
     * @param rewards     what the bounty (or, on login, all bounties) offers
     * @param bounties    how many active bounties are on the target now
     * @param onLogin     summary shown on login rather than a fresh bounty
     */
    public record BountyAlertPayload(Optional<String> sponsorName, List<ItemStack> rewards, int bounties, boolean onLogin)
            implements CustomPacketPayload {
        public static final Type<BountyAlertPayload> TYPE = BCPayloads.payloadType("bounty_alert");
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyAlertPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), BountyAlertPayload::sponsorName,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, BountyAlertPayload::rewards,
                ByteBufCodecs.VAR_INT, BountyAlertPayload::bounties,
                ByteBufCodecs.BOOL, BountyAlertPayload::onLogin,
                BountyAlertPayload::new);

        @Override
        public Type<BountyAlertPayload> type() {
            return TYPE;
        }
    }

    /** {@code count} stacks wait in the board's "to claim" tab. */
    public record RewardsAvailablePayload(int count) implements CustomPacketPayload {
        public static final Type<RewardsAvailablePayload> TYPE = BCPayloads.payloadType("rewards_available");
        public static final StreamCodec<RegistryFriendlyByteBuf, RewardsAvailablePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, RewardsAvailablePayload::count, RewardsAvailablePayload::new);

        @Override
        public Type<RewardsAvailablePayload> type() {
            return TYPE;
        }
    }
}
