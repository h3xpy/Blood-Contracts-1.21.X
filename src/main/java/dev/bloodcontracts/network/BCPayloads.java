package dev.bloodcontracts.network;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.BountyType;
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

    /** Upper bound for UUID lists read from the network (the real limit, {@code maxHunterListSize}, is checked by the manager). */
    public static final int MAX_HUNTERS_ON_WIRE = 1024;

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<UUID>> UUID_LIST =
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_HUNTERS_ON_WIRE));

    /**
     * Confirm the open creation menu: the rewards are whatever the server-side slots hold.
     *
     * @param hunters the private hunter list (must be empty for a public bounty; revalidated by the manager)
     */
    public record CreateBountyRequest(UUID target, BountyType bountyType, List<UUID> hunters) implements CustomPacketPayload {
        public static final Type<CreateBountyRequest> TYPE = BCPayloads.payloadType("create_bounty");
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateBountyRequest> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, CreateBountyRequest::target,
                NeoForgeStreamCodecs.enumCodec(BountyType.class), CreateBountyRequest::bountyType,
                UUID_LIST, CreateBountyRequest::hunters,
                CreateBountyRequest::new);

        /** A public bounty. */
        public CreateBountyRequest(UUID target) {
            this(target, BountyType.PUBLIC, List.of());
        }

        @Override
        public Type<CreateBountyRequest> type() {
            return TYPE;
        }
    }

    /** Widen a private bounty: add hunters. There is deliberately no payload to remove one. */
    public record AddHuntersRequest(UUID bountyId, List<UUID> hunters) implements CustomPacketPayload {
        public static final Type<AddHuntersRequest> TYPE = BCPayloads.payloadType("add_hunters");
        public static final StreamCodec<RegistryFriendlyByteBuf, AddHuntersRequest> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, AddHuntersRequest::bountyId, UUID_LIST, AddHuntersRequest::hunters, AddHuntersRequest::new);

        @Override
        public Type<AddHuntersRequest> type() {
            return TYPE;
        }
    }

    /** Widen a private bounty to everyone. There is deliberately no payload for the other direction. */
    public record MakePublicRequest(UUID bountyId) implements CustomPacketPayload {
        public static final Type<MakePublicRequest> TYPE = BCPayloads.payloadType("make_public");
        public static final StreamCodec<RegistryFriendlyByteBuf, MakePublicRequest> STREAM_CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, MakePublicRequest::bountyId, MakePublicRequest::new);

        @Override
        public Type<MakePublicRequest> type() {
            return TYPE;
        }
    }

    /** Withdraw a pending cancellation. */
    public record ResumeBountyRequest(UUID bountyId) implements CustomPacketPayload {
        public static final Type<ResumeBountyRequest> TYPE = BCPayloads.payloadType("resume_bounty");
        public static final StreamCodec<RegistryFriendlyByteBuf, ResumeBountyRequest> STREAM_CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, ResumeBountyRequest::bountyId, ResumeBountyRequest::new);

        @Override
        public Type<ResumeBountyRequest> type() {
            return TYPE;
        }
    }

    /** Start the cancel notice (the bounty stays collectable during the notice). */
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
     * @param wanted     one entry (poster) per target with active bounties, already sorted for the viewer
     * @param serverNow  the server clock when sent, so countdowns do not depend on the client's clock
     * @param openScreen open the board screen if it is not open yet
     * @param status     feedback for the last request (success or refusal), if any
     */
    public record BountyListPayload(List<TargetView> wanted, List<BountyView> history, List<DeliveryView> claimable, long serverNow,
                                    boolean openScreen, Optional<Component> status, boolean statusIsError) implements CustomPacketPayload {
        public static final Type<BountyListPayload> TYPE = BCPayloads.payloadType("bounty_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyListPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    TargetView.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.wanted);
                    BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.history);
                    DeliveryView.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.claimable);
                    buf.writeLong(p.serverNow);
                    buf.writeBoolean(p.openScreen);
                    ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC).encode(buf, p.status);
                    buf.writeBoolean(p.statusIsError);
                },
                buf -> new BountyListPayload(
                        TargetView.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        BountyView.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        DeliveryView.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readLong(),
                        buf.readBoolean(),
                        ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC).decode(buf),
                        buf.readBoolean()));

        /** All active bounties, flattened. */
        public List<BountyView> active() {
            return wanted.stream().flatMap(t -> t.bounties().stream()).toList();
        }

        @Override
        public Type<BountyListPayload> type() {
            return TYPE;
        }
    }

    /** Server settings shown in the "How it works" tab, sent at login. The tab never hard-codes a value. */
    public record HelpConfigPayload(int cancelNoticeMinutes, int maxHeartsLostPerDeath, int maxHeartsLost, int maxHeartsGained,
                                    boolean progressiveRegen, int regenOnlineStepMinutes, int regenOfflineStepMinutes,
                                    int immunityMinutes, int attributionWindowTicks, int maxActiveBountiesPerSponsor,
                                    int maxHunterListSize, boolean creditTamedPets) implements CustomPacketPayload {
        public static final Type<HelpConfigPayload> TYPE = BCPayloads.payloadType("help_config");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelpConfigPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.cancelNoticeMinutes);
                    buf.writeVarInt(p.maxHeartsLostPerDeath);
                    buf.writeVarInt(p.maxHeartsLost);
                    buf.writeVarInt(p.maxHeartsGained);
                    buf.writeBoolean(p.progressiveRegen);
                    buf.writeVarInt(p.regenOnlineStepMinutes);
                    buf.writeVarInt(p.regenOfflineStepMinutes);
                    buf.writeVarInt(p.immunityMinutes);
                    buf.writeVarInt(p.attributionWindowTicks);
                    buf.writeVarInt(p.maxActiveBountiesPerSponsor);
                    buf.writeVarInt(p.maxHunterListSize);
                    buf.writeBoolean(p.creditTamedPets);
                },
                buf -> new HelpConfigPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean()));

        /** The values in force on this server. */
        public static HelpConfigPayload fromConfig() {
            return new HelpConfigPayload(BCConfig.get(BCConfig.CANCEL_NOTICE_MINUTES), BCConfig.get(BCConfig.MAX_HEARTS_LOST_PER_DEATH),
                    BCConfig.get(BCConfig.MAX_HEARTS_LOST), BCConfig.get(BCConfig.MAX_HEARTS_GAINED),
                    BCConfig.heartRegenMode() == BCConfig.HeartRegenMode.PROGRESSIVE,
                    BCConfig.get(BCConfig.HEART_REGEN_ONLINE_STEP_MINUTES), BCConfig.get(BCConfig.HEART_REGEN_OFFLINE_STEP_MINUTES),
                    BCConfig.get(BCConfig.TARGET_IMMUNITY_MINUTES), BCConfig.attributionWindowTicks(),
                    BCConfig.maxActiveBountiesPerSponsor(), BCConfig.maxHunterListSize(), BCConfig.creditTamedPets());
        }

        @Override
        public Type<HelpConfigPayload> type() {
            return TYPE;
        }
    }

    /**
     * Every player the server knows (online or not), for the creation screen's target picker, with the recurring
     * hunter-target pairs that screen shows.
     */
    public record KnownPlayersPayload(List<KnownPlayer> players, List<TargetView.RepeatView> repeatPairs) implements CustomPacketPayload {
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
                KnownPlayer.STREAM_CODEC.apply(ByteBufCodecs.list()), KnownPlayersPayload::players,
                TargetView.RepeatView.STREAM_CODEC.apply(ByteBufCodecs.list()), KnownPlayersPayload::repeatPairs,
                KnownPlayersPayload::new);

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

    /** The target of one of the player's bounties was killed under contract. */
    public record TargetDownPayload(UUID target, String targetName, String hunterName, boolean heartGained) implements CustomPacketPayload {
        public static final Type<TargetDownPayload> TYPE = BCPayloads.payloadType("target_down");
        public static final StreamCodec<RegistryFriendlyByteBuf, TargetDownPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TargetDownPayload::target,
                ByteBufCodecs.STRING_UTF8, TargetDownPayload::targetName,
                ByteBufCodecs.STRING_UTF8, TargetDownPayload::hunterName,
                ByteBufCodecs.BOOL, TargetDownPayload::heartGained,
                TargetDownPayload::new);

        @Override
        public Type<TargetDownPayload> type() {
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
