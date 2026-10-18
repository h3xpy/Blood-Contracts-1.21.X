package dev.bloodcontracts.network;

import dev.bloodcontracts.BloodContracts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payloads. Client → server payloads are intentions only: they carry ids, never items or rewards.
 */
public final class BCPayloads {
    private BCPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, path));
    }

    // ---------------------------------------------------------------- client → server

    /** Confirm the open creation menu: the reward is whatever the server-side slot holds. */
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

    /**
     * Bounty board contents.
     *
     * @param openScreen open the board screen if it is not open yet
     * @param status     feedback for the last request (success or refusal), if any
     */
    public record BountyListPayload(List<BountyView> active, List<BountyView> history, boolean openScreen,
                                    Optional<Component> status, boolean statusIsError) implements CustomPacketPayload {
        public static final Type<BountyListPayload> TYPE = BCPayloads.payloadType("bounty_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyListPayload> STREAM_CODEC = StreamCodec.composite(
                BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()), BountyListPayload::active,
                BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()), BountyListPayload::history,
                ByteBufCodecs.BOOL, BountyListPayload::openScreen,
                ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC), BountyListPayload::status,
                ByteBufCodecs.BOOL, BountyListPayload::statusIsError,
                BountyListPayload::new);

        @Override
        public Type<BountyListPayload> type() {
            return TYPE;
        }
    }
}
