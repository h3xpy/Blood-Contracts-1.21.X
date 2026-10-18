package dev.bloodcontracts.network;

import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.BCPayloads.OpenCreateMenuRequest;
import dev.bloodcontracts.network.BCPayloads.RequestBountyList;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BCNetwork {
    private BCNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CreateBountyRequest.TYPE, CreateBountyRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleCreate(player, payload)));
        registrar.playToServer(CancelBountyRequest.TYPE, CancelBountyRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, player -> ServerPayloadHandler.handleCancel(player, payload)));
        registrar.playToServer(RequestBountyList.TYPE, RequestBountyList.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, ServerPayloadHandler::handleListRequest));
        registrar.playToServer(OpenCreateMenuRequest.TYPE, OpenCreateMenuRequest.STREAM_CODEC,
                (payload, ctx) -> onServer(ctx, ServerPayloadHandler::handleOpenCreate));
        // The lambda body only runs on the client, so the client class is never loaded on a dedicated server.
        registrar.playToClient(BountyListPayload.TYPE, BountyListPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.bloodcontracts.client.ClientPayloadHandler.handleList(payload)));
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
