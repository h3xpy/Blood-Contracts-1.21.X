package dev.bloodcontracts.debug;

import com.mojang.logging.LogUtils;
import dev.bloodcontracts.client.BountyAlertOverlay;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Development-only scripted client run ({@code ./gradlew runAlertDebug}): once in the world, triggers the bounty alert,
 * then switches between screens while taking screenshots (run/screenshots/alert_*.png), and quits.
 */
@Mod(value = "bloodcontracts_debug", dist = Dist.CLIENT)
public class AlertScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TreeMap<Integer, Runnable> steps = new TreeMap<>();
    private int tick = -1;
    private int inWorldFor;

    public AlertScenario() {
        if (!"alert".equals(System.getProperty("bloodcontracts.debugScenario"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        step(0, () -> BountyAlertOverlay.trigger(new BountyAlertPayload(Optional.of("Sponsor_42"),
                List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.TOTEM_OF_UNDYING)), 2, false)));
        shot(12, "01_hud");
        step(20, () -> mc.setScreen(new InventoryScreen(mc.player)));
        shot(21, "02_inventory_first_tick");
        shot(26, "03_inventory");
        step(30, () -> mc.setScreen(new ChatScreen("")));
        shot(31, "04_chat_first_tick");
        shot(36, "05_chat");
        step(40, () -> mc.setScreen(new dev.bloodcontracts.client.BountyBoardScreen()));
        shot(41, "06_board_first_tick");
        shot(46, "07_board");
        step(50, () -> mc.setScreen(null));
        shot(51, "08_back_first_tick");
        shot(56, "09_back");
        step(60, () -> mc.setScreen(new PauseScreen(true)));
        shot(61, "10_pause_first_tick");
        shot(66, "11_pause");
        step(70, () -> mc.setScreen(null));
        step(72, () -> mc.options.hideGui = true);
        shot(76, "12_f1");
        step(78, () -> mc.options.hideGui = false);
        shot(100, "13_late");
        shot(130, "14_fading");
        step(160, mc::stop);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick(mc));
    }

    private void step(int at, Runnable r) {
        steps.merge(at, r, (a, b) -> () -> {
            a.run();
            b.run();
        });
    }

    private void shot(int at, String name) {
        step(at, () -> Screenshot.grab(Minecraft.getInstance().gameDirectory, "alert_" + name + ".png",
                Minecraft.getInstance().getMainRenderTarget(), msg -> LOGGER.info("[alert-debug] {}", msg.getString())));
    }

    private void onTick(Minecraft mc) {
        if (tick < 0) {
            // Wait until the world is really on screen, plus a moment for chunks.
            if (mc.player == null || mc.level == null || mc.screen != null || ++inWorldFor < 60) {
                return;
            }
            tick = 0;
            LOGGER.info("[alert-debug] scenario started");
        }
        Runnable r = steps.get(tick);
        if (r != null) {
            LOGGER.info("[alert-debug] tick {} (screen: {})", tick, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            r.run();
        }
        tick++;
    }
}
