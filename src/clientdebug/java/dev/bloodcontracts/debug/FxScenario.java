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
    /** Balance to give the respawned player, as BountyManager does from its saved transfers (null: none). */
    private static volatile Integer respawnBalance;

    public FxScenario() {
        if (!"fx".equals(System.getProperty("bloodcontracts.debugScenario"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // The alert arrives while the chat is open: nothing (no picture, no sound) until the chat closes.
        step(0, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        shot(14, "00_alert_waiting_in_chat");
        step(20, () -> mc.setScreen(null));
        step(2, () -> {
            onServer(mc, p -> {
                // survival to see the hearts, but nothing may hurt the player during the scenario
                p.setInvulnerable(true);
                p.serverLevel().setDayTime(6000);
                p.setHealth(p.getMaxHealth());
                p.setGameMode(GameType.SURVIVAL);
                p.getAttribute(Attributes.MAX_HEALTH).removeModifier(BountyManager.HEART_MODIFIER);
            });
            BountyAlertOverlay.trigger(new BountyAlertPayload(Optional.of("Ysolde"),
                    List.of(new ItemStack(Items.DIAMOND, 12), new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.TOTEM_OF_UNDYING)), 3, false));
        });
        shot(24, "01_alert_opening");
        shot(31, "02_alert_title_falling");
        shot(50, "03_alert_full");
        step(60, () -> mc.setScreen(new InventoryScreen(mc.player)));
        shot(66, "04_alert_paused_under_inventory");
        step(80, () -> mc.setScreen(null));
        shot(86, "05_alert_resumed");
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
        step(305, () -> onServer(mc, p -> p.setHealth(p.getMaxHealth())));
        step(310, () -> onServer(mc, p -> setBalance(p, -2)));
        shot(318, "11_heart_loss_draining");
        shot(338, "12_heart_loss_breaking");
        shot(375, "13_heart_lost");
        step(380, () -> onServer(mc, p -> setBalance(p, -1)));
        shot(386, "14_half_heart_back");
        step(400, () -> onServer(mc, p -> p.getAttribute(Attributes.MAX_HEALTH).removeModifier(BountyManager.HEART_MODIFIER)));
        // Killed under contract: the heart is lost while dead; the animation must play after the respawn, not before.
        step(410, () -> onServer(mc, p -> {
            p.setInvulnerable(false);
            respawnBalance = -2;
            setBalance(p, -2);
            p.kill();
        }));
        shot(425, "15_dead_no_animation_yet");
        step(430, () -> {
            mc.player.respawn();
            mc.setScreen(null);
        });
        shot(452, "16a_after_respawn_settling");
        shot(462, "16_after_respawn_heart_draining");
        shot(482, "17_after_respawn_heart_breaking");
        step(500, () -> onServer(mc, p -> p.getAttribute(Attributes.MAX_HEALTH).removeModifier(BountyManager.HEART_MODIFIER)));
        step(505, mc::stop);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick(mc));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) -> {
            if (respawnBalance != null && e.getEntity() instanceof ServerPlayer p) {
                setBalance(p, respawnBalance);
            }
        });
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
