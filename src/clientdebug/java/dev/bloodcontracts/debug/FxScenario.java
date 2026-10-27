package dev.bloodcontracts.debug;

import com.mojang.logging.LogUtils;
import dev.bloodcontracts.client.BloodToast;
import dev.bloodcontracts.client.BountyAlertOverlay;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Development-only scripted client run ({@code ./gradlew runFxDebug}): the bounty alert in game and under a menu, the
 * notification toasts, and a heart gained then lost (through the real server-side modifier), with screenshots
 * (run/screenshots/fx_*.png).
 */
@Mod(value = "bloodcontracts_debug", dist = Dist.CLIENT)
public class FxScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TreeMap<Integer, Runnable> steps = new TreeMap<>();
    private int tick = -1;
    private int inWorldFor;

    public FxScenario() {
        if (!"fx".equals(System.getProperty("bloodcontracts.debugScenario"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        step(0, () -> {
            onServer(mc, p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.getAttribute(Attributes.MAX_HEALTH).removeModifier(BountyManager.HEART_MODIFIER);
            });
            BountyAlertOverlay.trigger(new BountyAlertPayload(Optional.of("Grumm"),
                    List.of(new ItemStack(Items.DIAMOND, 12), new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.TOTEM_OF_UNDYING)), 3, false));
        });
        shot(4, "01_alert_opening");
        shot(11, "02_alert_title_falling");
        shot(30, "03_alert_full");
        step(40, () -> mc.setScreen(new InventoryScreen(mc.player)));
        shot(46, "04_alert_paused_under_inventory");
        step(60, () -> mc.setScreen(null));
        shot(66, "05_alert_resumed");
        // let the alert finish (140 ticks of game time from its start)
        step(190, () -> {
            mc.getToasts().addToast(BloodToast.rewards(3));
            mc.getToasts().addToast(BloodToast.targetDown(new BCPayloads.TargetDownPayload(
                    UUID.nameUUIDFromBytes("Brigand".getBytes()), "Brigand", "Fennec", true)));
        });
        shot(205, "06_toasts");
        step(260, () -> onServer(mc, p -> setBalance(p, 2)));
        shot(268, "07_heart_gain_appears");
        shot(280, "08_heart_gain_flying");
        shot(287, "09_heart_gain_landing");
        shot(300, "10_heart_gained");
        step(310, () -> onServer(mc, p -> setBalance(p, -2)));
        shot(316, "11_heart_loss_shaking");
        shot(326, "12_heart_loss_breaking");
        shot(345, "13_heart_lost");
        step(350, () -> onServer(mc, p -> setBalance(p, -1)));
        shot(357, "14_half_heart_back");
        step(370, () -> onServer(mc, p -> p.getAttribute(Attributes.MAX_HEALTH).removeModifier(BountyManager.HEART_MODIFIER)));
        step(380, mc::stop);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick(mc));
    }

    private static void setBalance(ServerPlayer p, int hp) {
        var max = p.getAttribute(Attributes.MAX_HEALTH);
        max.removeModifier(BountyManager.HEART_MODIFIER);
        max.addTransientModifier(new AttributeModifier(BountyManager.HEART_MODIFIER, hp, AttributeModifier.Operation.ADD_VALUE));
        p.setHealth(Math.min(p.getHealth(), p.getMaxHealth()));
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                action.accept(p);
            }
        });
    }

    private void step(int at, Runnable r) {
        steps.merge(at, r, (a, b) -> () -> {
            a.run();
            b.run();
        });
    }

    private void shot(int at, String name) {
        step(at, () -> Screenshot.grab(Minecraft.getInstance().gameDirectory, "fx_" + name + ".png",
                Minecraft.getInstance().getMainRenderTarget(), msg -> LOGGER.info("[fx-debug] {}", msg.getString())));
    }

    private void onTick(Minecraft mc) {
        if (tick < 0) {
            if (mc.player == null || mc.level == null || mc.screen != null || ++inWorldFor < 60) {
                return;
            }
            tick = 0;
            LOGGER.info("[fx-debug] scenario started");
        }
        Runnable r = steps.get(tick);
        if (r != null) {
            r.run();
        }
        tick++;
    }
}
