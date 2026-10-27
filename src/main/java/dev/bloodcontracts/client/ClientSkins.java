package dev.bloodcontracts.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Real skins for every player shown by the mod, including offline ones.
 * <ol>
 *   <li>Online: the skin from the tab list.</li>
 *   <li>Offline: the signed skin the server recorded at their last login (sent with the known players).</li>
 *   <li>Otherwise: looked up once from Mojang's session server by UUID (online-mode accounts).</li>
 * </ol>
 * Loading happens in the background; the default skin is shown only until the real one is ready.
 */
final class ClientSkins {
    private static final Map<UUID, PlayerSkin> LOADED = new ConcurrentHashMap<>();
    private static final Set<UUID> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, KnownPlayer> KNOWN = new ConcurrentHashMap<>();

    private ClientSkins() {}

    /** Server-provided names and skins; a new skin replaces a cached one. */
    static void update(List<KnownPlayer> players) {
        for (KnownPlayer p : players) {
            KnownPlayer previous = KNOWN.put(p.id(), p);
            if (previous != null && !previous.skin().equals(p.skin())) {
                LOADED.remove(p.id());
                REQUESTED.remove(p.id());
            }
        }
    }

    /** A profile sent with a wanted poster: same as a known player, with its recorded skin. */
    static void remember(GameProfile profile) {
        Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        if (textures == null && KNOWN.containsKey(profile.getId())) {
            return; // nothing new: keep the skin we already have
        }
        update(List.of(new KnownPlayer(profile.getId(), profile.getName(), false,
                java.util.Optional.ofNullable(textures).map(Property::value),
                java.util.Optional.ofNullable(textures).map(Property::signature))));
    }

    static PlayerSkin get(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(player) : null;
        if (info != null) {
            return info.getSkin();
        }
        PlayerSkin loaded = LOADED.get(player);
        if (loaded != null) {
            return loaded;
        }
        if (REQUESTED.add(player)) {
            load(mc, player);
        }
        return DefaultPlayerSkin.get(player);
    }

    private static void load(Minecraft mc, UUID player) {
        KnownPlayer known = KNOWN.get(player);
        CompletableFuture.supplyAsync(() -> {
            String name = known != null ? known.name() : "";
            GameProfile profile = new GameProfile(player, name);
            if (known != null && known.skin().isPresent()) {
                profile.getProperties().put("textures", new Property("textures", known.skin().get(), known.skinSignature().orElse(null)));
                return profile;
            }
            try {
                ProfileResult result = mc.getMinecraftSessionService().fetchProfile(player, false);
                return result != null ? result.profile() : profile;
            } catch (RuntimeException e) {
                BloodContracts.LOGGER.debug("Blood Contracts: no skin found for {}", player, e);
                return profile;
            }
        }, Util.backgroundExecutor())
                .thenCompose(profile -> mc.getSkinManager().getOrLoad(profile))
                .thenAccept(skin -> LOADED.put(player, skin))
                .exceptionally(e -> {
                    // Keep the default skin; try again next time the screen opens.
                    REQUESTED.remove(player);
                    return null;
                });
    }

    /** Forget everything (leaving a server). */
    static void clear() {
        LOADED.clear();
        REQUESTED.clear();
        KNOWN.clear();
    }
}
