package dev.bloodcontracts.client;

import dev.bloodcontracts.network.BCPayloads;
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

    /** Recurring hunter-target pairs, for the creation screen. */
    static List<dev.bloodcontracts.network.TargetView.RepeatView> repeatPairs = List.of();

    /** Server settings for the help tab (defaults until the server sends its own at login). */
    static BCPayloads.HelpConfigPayload helpConfig = BCPayloads.HelpConfigPayload.fromConfig();

    private ClientPayloadHandler() {}

    public static void handleHelpConfig(BCPayloads.HelpConfigPayload payload) {
        helpConfig = payload;
    }

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
        repeatPairs = List.copyOf(payload.repeatPairs());
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
