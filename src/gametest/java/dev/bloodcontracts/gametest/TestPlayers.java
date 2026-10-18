package dev.bloodcontracts.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

/**
 * Simulated players: real {@link ServerPlayer}s on an embedded connection, joined through the player list (so
 * {@code PlayerLoggedInEvent} fires), in survival mode and without spawn protection.
 */
public final class TestPlayers {
    private static final List<ServerPlayer> CREATED = new ArrayList<>();
    private static int counter;

    private TestPlayers() {}

    public static ServerPlayer sponsor(GameTestHelper helper) {
        return create(helper, "sponsor", new Vec3(1.5, 1, 1.5));
    }

    public static ServerPlayer hunter(GameTestHelper helper) {
        return create(helper, "hunter", new Vec3(3.5, 1, 1.5));
    }

    public static ServerPlayer target(GameTestHelper helper) {
        return create(helper, "target", new Vec3(5.5, 1, 1.5));
    }

    public static ServerPlayer create(GameTestHelper helper, String role, Vec3 relativePos) {
        String name = role + "_" + counter++;
        name = name.substring(0, Math.min(16, name.length()));
        return login(helper, new GameProfile(UUID.randomUUID(), name), relativePos);
    }

    /** Logs a player in with the given profile (used to simulate a reconnection with the same UUID). */
    public static ServerPlayer login(GameTestHelper helper, GameProfile profile, Vec3 relativePos) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "spawnInvulnerableTime");
        Vec3 pos = helper.absoluteVec(relativePos);
        player.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, 0, 0);
        CREATED.add(player);
        return player;
    }

    /** Disconnects the player (fires {@code PlayerLoggedOutEvent}). */
    public static void logout(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && server.getPlayerList().getPlayer(player.getUUID()) == player) {
            server.getPlayerList().remove(player);
        }
    }

    /** Logs out every simulated player left over by previous tests. */
    public static void logoutAll() {
        for (ServerPlayer p : List.copyOf(CREATED)) {
            if (!(p instanceof FakePlayer)) {
                logout(p);
            }
        }
        CREATED.clear();
    }
}
