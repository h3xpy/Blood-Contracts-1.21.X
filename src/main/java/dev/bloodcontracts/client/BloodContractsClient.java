package dev.bloodcontracts.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.menu.BCMenus;
import dev.bloodcontracts.network.BCPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import org.lwjgl.glfw.GLFW;

@Mod(value = BloodContracts.MODID, dist = Dist.CLIENT)
public class BloodContractsClient {
    static final KeyMapping OPEN_BOARD = new KeyMapping("key.bloodcontracts.board", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.bloodcontracts");

    public BloodContractsClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(BCMenus.BOUNTY_CREATE.get(), BountyCreateScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_BOARD));
        // The bounty alert: a HUD layer in game, and drawn again above any open screen so it cannot be missed.
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(BountyAlertOverlay.LAYER, (g, delta) -> {
            if (Minecraft.getInstance().screen == null) {
                BountyAlertOverlay.render(g, delta);
            }
        }));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH, HeartAnimations.LAYER, HeartAnimations::render));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) ->
                BountyAlertOverlay.render(e.getGuiGraphics(), Minecraft.getInstance().getTimer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientSkins.clear();
            ClientPayloadHandler.knownPlayers = List.of();
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            BountyAlertOverlay.tick();
            HeartAnimations.tick();
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_BOARD.consumeClick()) {
                if (mc.player != null && mc.screen == null && mc.getConnection() != null) {
                    mc.setScreen(new BountyBoardScreen());
                    PacketDistributor.sendToServer(BCPayloads.RequestBountyList.INSTANCE);
                }
            }
        });
    }
}
