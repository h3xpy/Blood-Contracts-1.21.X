package dev.bloodcontracts.network;

import dev.bloodcontracts.network.BCPayloads.AddHuntersRequest;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.BCPayloads.MakePublicRequest;
import dev.bloodcontracts.network.BCPayloads.OpenCreateMenuRequest;
import dev.bloodcontracts.network.BCPayloads.RequestBountyList;
import dev.bloodcontracts.network.BCPayloads.ResumeBountyRequest;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BCNetwork {
    private BCNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // Version 2: v2 payloads (types, widening, notice, grouped views). A 1.x client cannot join a 2.x server.
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(CreateBountyRequest.TYPE, CreateBountyRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleCreate(player, payload)));
        registrar.playToServer(AddHuntersRequest.TYPE, AddHuntersRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleAddHunters(player, payload)));
        registrar.playToServer(MakePublicRequest.TYPE, MakePublicRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleMakePublic(player, payload)));
        registrar.playToServer(CancelBountyRequest.TYPE, CancelBountyRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleCancel(player, payload)));
        registrar.playToServer(ResumeBountyRequest.TYPE, ResumeBountyRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleResume(player, payload)));
        registrar.playToServer(BCPayloads.ClaimRewardRequest.TYPE, BCPayloads.ClaimRewardRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleClaim(player, payload)));
        registrar.playToServer(RequestBountyList.TYPE, RequestBountyList.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, ServerPayloadHandler::handleListRequest));
        registrar.playToServer(OpenCreateMenuRequest.TYPE, OpenCreateMenuRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, ServerPayloadHandler::handleOpenCreate));
        // The lambda body only runs on the client, so the client class is never loaded on a dedicated server.
        registrar.playToClient(BountyListPayload.TYPE, BountyListPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleList(payload)));
        registrar.playToClient(BCPayloads.KnownPlayersPayload.TYPE, BCPayloads.KnownPlayersPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleKnownPlayers(payload)));
        registrar.playToClient(BCPayloads.BountyAlertPayload.TYPE, BCPayloads.BountyAlertPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleAlert(payload)));
        registrar.playToClient(BCPayloads.RewardsAvailablePayload.TYPE, BCPayloads.RewardsAvailablePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleRewardsAvailable(payload)));
        registrar.playToClient(BCPayloads.HelpConfigPayload.TYPE, BCPayloads.HelpConfigPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleHelpConfig(payload)));
    }

    /** Runs the handler on the server thread, for a server player only. */
    private static void onServer(IPayloadContext ctx, java.util.function.Consumer<ServerPlayer> handler) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                handler.accept(player);
            }
        });
    }
}
