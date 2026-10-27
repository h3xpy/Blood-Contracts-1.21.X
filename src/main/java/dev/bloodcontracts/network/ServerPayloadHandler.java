package dev.bloodcontracts.network;

import com.mojang.authlib.GameProfile;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.RepeatPair;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads.AddHuntersRequest;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.ClaimRewardRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.BCPayloads.DeliveryView;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import dev.bloodcontracts.network.BCPayloads.MakePublicRequest;
import dev.bloodcontracts.network.BCPayloads.ResumeBountyRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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
        BountyResult<Bounty> result = manager.create(player, request.target(), request.bountyType(), request.hunters(),
                menu.rewardContainer(), menu.activeRewardSlots());
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

    public static BountyResult<Bounty> handleAddHunters(ServerPlayer player, AddHuntersRequest request) {
        return respond(player, BountyManager.get(player.server).addHunters(player.getUUID(), request.bountyId(), request.hunters()),
                b -> Component.translatable("bloodcontracts.gui.hunters_added", b.hunters().size()));
    }

    public static BountyResult<Bounty> handleMakePublic(ServerPlayer player, MakePublicRequest request) {
        return respond(player, BountyManager.get(player.server).makePublic(player.getUUID(), request.bountyId()),
                b -> Component.translatable("bloodcontracts.gui.made_public"));
    }

    /** Starts the cancel notice (sponsor only; operators use the admin command for an immediate cancel). */
    public static BountyResult<Bounty> handleCancel(ServerPlayer player, CancelBountyRequest request) {
        return respond(player, BountyManager.get(player.server).requestCancel(player.getUUID(), request.bountyId()),
                b -> b.isActive()
                        ? Component.translatable("bloodcontracts.gui.cancel_requested", BCConfig.get(BCConfig.CANCEL_NOTICE_MINUTES))
                        : Component.translatable("bloodcontracts.gui.cancelled"));
    }

    public static BountyResult<Bounty> handleResume(ServerPlayer player, ResumeBountyRequest request) {
        return respond(player, BountyManager.get(player.server).resume(player.getUUID(), request.bountyId()),
                b -> Component.translatable("bloodcontracts.gui.resumed"));
    }

    private static BountyResult<Bounty> respond(ServerPlayer player, BountyResult<Bounty> result, Function<Bounty, Component> success) {
        sendList(player, false, result.isOk() ? success.apply(result.value()) : result.error().message(), !result.isOk());
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
        return new KnownPlayersPayload(players, repeatViews(manager, null));
    }

    private static List<TargetView.RepeatView> repeatViews(BountyManager manager, @Nullable UUID target) {
        return manager.repeatPairs(target).stream()
                .map((RepeatPair r) -> new TargetView.RepeatView(r.target(), manager.nameOf(r.hunter()), r.count()))
                .toList();
    }

    /** The target's profile with the skin recorded at their last login (or their live one if online). */
    public static GameProfile profileOf(BountyManager manager, ServerPlayer viewer, UUID target) {
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(target);
        if (online != null) {
            return online.getGameProfile();
        }
        GameProfile profile = new GameProfile(target, manager.nameOf(target));
        manager.data().skin(target).ifPresent(textures -> profile.getProperties().put("textures", textures));
        return profile;
    }

    /**
     * The wanted posters for {@code viewer}: one per target, every active bounty on it (oldest first), sorted with the
     * targets the viewer can collect on first, then the most recent.
     */
    public static List<TargetView> wanted(ServerPlayer viewer) {
        BountyManager manager = BountyManager.get(viewer.server);
        boolean op = viewer.hasPermissions(Commands.LEVEL_GAMEMASTERS);
        Map<UUID, List<BountyView>> byTarget = new LinkedHashMap<>();
        manager.activeBounties().stream()
                .sorted(Comparator.comparingLong(Bounty::createdAt))
                .forEach(b -> byTarget.computeIfAbsent(b.target(), t -> new ArrayList<>()).add(BountyView.of(b, manager, viewer.getUUID(), op)));
        List<TargetView> out = new ArrayList<>();
        byTarget.forEach((target, views) -> out.add(new TargetView(profileOf(manager, viewer, target), manager.immuneUntil(target),
                List.copyOf(views), repeatViews(manager, target))));
        out.sort(Comparator.comparing(TargetView::eligible).reversed().thenComparing(Comparator.comparingLong(TargetView::newest).reversed()));
        return out;
    }

    public static BountyListPayload buildList(ServerPlayer player, boolean openScreen, @Nullable Component status, boolean isError) {
        BountyManager manager = BountyManager.get(player.server);
        boolean op = player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
        List<BountyView> history = manager.history().stream().limit(HISTORY_SENT)
                .map(b -> BountyView.of(b, manager, player.getUUID(), op)).toList();
        List<DeliveryView> claimable = manager.deliveriesFor(player.getUUID()).stream()
                .map(d -> new DeliveryView(d.id(), DisplayItems.lite(d.stack()), d.reason(),
                        manager.findAny(d.bountyId()).map(b -> manager.nameOf(b.target())).orElse("?")))
                .toList();
        return new BountyListPayload(wanted(player), history, claimable, manager.now(), openScreen, Optional.ofNullable(status), isError);
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
