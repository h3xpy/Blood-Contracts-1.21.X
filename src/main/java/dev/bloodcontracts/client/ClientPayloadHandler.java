package dev.bloodcontracts.client;

import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload;
import dev.bloodcontracts.network.BCPayloads.RewardsAvailablePayload;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client-side handling of server → client payloads. Only loaded on the client. */
public final class ClientPayloadHandler {
    /** Last list of players the server knows (online and offline), for the creation screen. */
    static List<KnownPlayersPayload.KnownPlayer> knownPlayers = List.of();

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

    public static void handleKnownPlayers(KnownPlayersPayload payload) {
        knownPlayers = List.copyOf(payload.players());
        ClientSkins.update(payload.players());
        if (Minecraft.getInstance().screen instanceof BountyCreateScreen create) {
            create.refreshCandidates();
        }
    }

    public static void handleAlert(BountyAlertPayload payload) {
        BountyAlertOverlay.trigger(payload);
    }

    public static void handleRewardsAvailable(RewardsAvailablePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new RewardToast(payload.count()));
        if (mc.screen instanceof BountyBoardScreen) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(dev.bloodcontracts.network.BCPayloads.RequestBountyList.INSTANCE);
        }
    }
}
