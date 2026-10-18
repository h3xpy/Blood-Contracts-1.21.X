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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = BloodContracts.MODID, dist = Dist.CLIENT)
public class BloodContractsClient {
    static final KeyMapping OPEN_BOARD = new KeyMapping("key.bloodcontracts.board", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.bloodcontracts");

    public BloodContractsClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(BCMenus.BOUNTY_CREATE.get(), BountyCreateScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_BOARD));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
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
