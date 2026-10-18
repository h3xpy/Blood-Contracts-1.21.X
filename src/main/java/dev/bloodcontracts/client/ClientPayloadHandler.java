package dev.bloodcontracts.client;

import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import net.minecraft.client.Minecraft;

/** Client-side handling of server → client payloads. Only loaded on the client. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleList(BountyListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BountyBoardScreen board) {
            board.update(payload);
        } else if (payload.openScreen()) {
            BountyBoardScreen board = new BountyBoardScreen();
            mc.setScreen(board);
            board.update(payload);
        }
    }
}
