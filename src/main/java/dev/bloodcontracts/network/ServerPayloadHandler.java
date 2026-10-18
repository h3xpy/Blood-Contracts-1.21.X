package dev.bloodcontracts.network;

import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side handling of client intentions. Everything is revalidated here (open menu, rights, bounty state) and
 * delegated to {@link BountyManager}. These methods are called on the server thread and can be called directly by
 * tests, without a client.
 */
public final class ServerPayloadHandler {
    private static final int HISTORY_SENT = 50;

    private ServerPayloadHandler() {}

    public static BountyResult<Bounty> handleCreate(ServerPlayer player, CreateBountyRequest request) {
        if (!(player.containerMenu instanceof BountyCreateMenu menu) || !menu.stillValid(player)) {
            return BountyResult.fail(BountyError.NO_MENU);
        }
        BountyResult<Bounty> result = BountyManager.get(player.server)
                .create(player, request.target(), menu.rewardContainer(), BountyCreateMenu.REWARD_SLOT);
        if (result.isOk()) {
            // The slot is empty now; closing just tidies up.
            player.closeContainer();
            Bounty b = result.value();
            sendList(player, true, Component.translatable("bloodcontracts.gui.created", b.reward().getHoverName(),
                    BountyManager.get(player.server).nameOf(b.target())), false);
        } else {
            sendStatusInMenu(player, result.error());
        }
        return result;
    }

    public static BountyResult<Bounty> handleCancel(ServerPlayer player, CancelBountyRequest request) {
        BountyResult<Bounty> result = BountyManager.get(player.server).cancel(player.getUUID(), request.bountyId(), false);
        sendList(player, false, result.isOk()
                ? Component.translatable("bloodcontracts.gui.cancelled")
                : result.error().message(), !result.isOk());
        return result;
    }

    public static void handleListRequest(ServerPlayer player) {
        sendList(player, false, null, false);
    }

    public static void handleOpenCreate(ServerPlayer player) {
        if (player.isAlive()) {
            player.openMenu(BountyCreateMenu.provider());
        }
    }

    public static BountyListPayload buildList(ServerPlayer player, boolean openScreen, Component status, boolean isError) {
        BountyManager manager = BountyManager.get(player.server);
        boolean op = player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
        List<BountyView> active = manager.activeBounties().stream()
                .map(b -> BountyView.of(b, manager, player.getUUID(), op)).toList();
        List<BountyView> history = manager.history().stream().limit(HISTORY_SENT)
                .map(b -> BountyView.of(b, manager, player.getUUID(), op)).toList();
        return new BountyListPayload(active, history, openScreen, Optional.ofNullable(status), isError);
    }

    public static void sendList(ServerPlayer player, boolean openScreen, Component status, boolean isError) {
        if (player.connection.hasChannel(BountyListPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, buildList(player, openScreen, status, isError));
        } else if (status != null) {
            // Client without the mod's screens: fall back to a chat message.
            player.sendSystemMessage(status);
        }
    }

    private static void sendStatusInMenu(ServerPlayer player, BountyError error) {
        player.displayClientMessage(error.message(), true);
    }
}
