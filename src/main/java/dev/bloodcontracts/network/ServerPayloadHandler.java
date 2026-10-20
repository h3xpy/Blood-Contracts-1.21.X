package dev.bloodcontracts.network;

import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.ClaimRewardRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.BCPayloads.DeliveryView;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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
        BountyManager manager = BountyManager.get(player.server);
        BountyResult<Bounty> result = manager.create(player, request.target(), menu.rewardContainer(), menu.activeRewardSlots());
        if (result.isOk()) {
            // The slots are empty now; closing just tidies up.
            player.closeContainer();
            Bounty b = result.value();
            sendList(player, true, Component.translatable("bloodcontracts.gui.created", b.rewards().size(), manager.nameOf(b.target())), false);
        } else {
            player.displayClientMessage(result.error().message(), true);
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

    public static BountyResult<Integer> handleClaim(ServerPlayer player, ClaimRewardRequest request) {
        BountyResult<Integer> result = BountyManager.get(player.server).claimRewards(player, request.deliveryId());
        sendList(player, false, result.isOk()
                ? Component.translatable("bloodcontracts.gui.claimed", result.value())
                : result.error().message(), !result.isOk());
        return result;
    }

    public static void handleListRequest(ServerPlayer player) {
        // The board shows target faces: send who they are (and their skins) along with the list.
        send(player, knownPlayers(player));
        sendList(player, false, null, false);
    }

    public static void handleOpenCreate(ServerPlayer player) {
        if (player.isAlive()) {
            player.openMenu(BountyCreateMenu.provider());
            send(player, knownPlayers(player));
        }
    }

    /**
     * Every player the server knows except the viewer, online first, with their last recorded skin (so offline players
     * keep their face). Offline players are valid targets.
     */
    public static KnownPlayersPayload knownPlayers(ServerPlayer viewer) {
        BountyManager manager = BountyManager.get(viewer.server);
        List<KnownPlayer> players = new ArrayList<>();
        for (Map.Entry<UUID, String> e : manager.data().knownPlayers().entrySet()) {
            if (e.getKey().equals(viewer.getUUID())) {
                continue;
            }
            var skin = manager.data().skin(e.getKey());
            players.add(new KnownPlayer(e.getKey(), e.getValue(), viewer.server.getPlayerList().getPlayer(e.getKey()) != null,
                    skin.map(com.mojang.authlib.properties.Property::value),
                    skin.map(com.mojang.authlib.properties.Property::signature)));
        }
        players.sort(Comparator.comparing(KnownPlayer::online).reversed()
                .thenComparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return new KnownPlayersPayload(players);
    }

    public static BountyListPayload buildList(ServerPlayer player, boolean openScreen, @Nullable Component status, boolean isError) {
        BountyManager manager = BountyManager.get(player.server);
        boolean op = player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
        List<BountyView> active = manager.activeBounties().stream()
                .map(b -> BountyView.of(b, manager, player.getUUID(), op)).toList();
        List<BountyView> history = manager.history().stream().limit(HISTORY_SENT)
                .map(b -> BountyView.of(b, manager, player.getUUID(), op)).toList();
        List<DeliveryView> claimable = manager.deliveriesFor(player.getUUID()).stream()
                .map(d -> new DeliveryView(d.id(), d.stack().copy(), d.reason(),
                        manager.findAny(d.bountyId()).map(b -> manager.nameOf(b.target())).orElse("?")))
                .toList();
        return new BountyListPayload(active, history, claimable, openScreen, Optional.ofNullable(status), isError);
    }

    public static void sendList(ServerPlayer player, boolean openScreen, @Nullable Component status, boolean isError) {
        if (openScreen) {
            send(player, knownPlayers(player));
        }
        if (!send(player, buildList(player, openScreen, status, isError)) && status != null) {
            // Client without the mod's screens: fall back to a chat message.
            player.sendSystemMessage(status);
        }
    }

    /** Sends a payload if the client understands it. */
    static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
            return true;
        }
        return false;
    }
}
