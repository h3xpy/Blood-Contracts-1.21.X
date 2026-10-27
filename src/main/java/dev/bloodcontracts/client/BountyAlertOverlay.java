package dev.bloodcontracts.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import java.util.List;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * "There is a price on your head": a full-screen, deliberately stressful alert (not a chat message). Pulsing blood-red
 * vignette, a blood ribbon across the middle of the screen with the title slamming onto it, a curse sting then an
 * accelerating heartbeat. While a menu is open the alert pauses and only a thin pulsing rim shows at the screen edges,
 * so it never covers the menu; it resumes once the player is back in the game.
 */
public final class BountyAlertOverlay {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "bounty_alert");
    private static final int DURATION = 140;
    private static final int LOGIN_DURATION = 100;

    /** Extra wait after joining a world before a login alert, so the player actually sees it. */
    private static final int LOGIN_GRACE = 40;

    private static @Nullable BountyAlertPayload current;
    /** The alert waits (age stays 0, no sound) until the player is in the world with no loading screen nor menu open. */
    private static boolean started;
    private static int waitTicks;
    private static int age;
    private static int duration;
    private static int nextBeat;
    private static final Random SHAKE = new Random();

    private BountyAlertOverlay() {}

    public static void trigger(BountyAlertPayload alert) {
        current = alert;
        started = false;
        age = 0;
        waitTicks = alert.onLogin() ? LOGIN_GRACE : 0;
        duration = alert.onLogin() ? LOGIN_DURATION : DURATION;
        nextBeat = 6;
    }

    /** True while the world is not on screen yet (joining, loading terrain, changing dimension). */
    private static boolean worldHidden(Minecraft mc) {
        return mc.level == null || mc.player == null
                || mc.screen instanceof ReceivingLevelScreen || mc.screen instanceof LevelLoadingScreen
                || mc.screen instanceof ConnectScreen || mc.getOverlay() != null;
    }

    private static void start() {
        started = true;
        var sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.9f));
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.5f, 1.0f));
    }

    /** Client tick: waits for the world, then runs the alert with a heartbeat that speeds up. */
    public static void tick() {
        if (current == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            // Not before the player can see it: no loading screen and no menu (settings, chat, inventory...).
            // The picture and the sound start together, as soon as they are back in the game.
            if (worldHidden(mc) || mc.screen != null) {
                return;
            }
            if (waitTicks-- > 0) {
                return;
            }
            start();
        }
        if (mc.screen != null) {
            return; // paused under menus: resumes, with its sound, once the player is back in the game
        }
        age++;
        if (age >= duration) {
            current = null;
            return;
        }
        if (age >= nextBeat && age < duration - 20) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0f + age / 300f, 1.0f));
            int interval = Math.max(7, 18 - age / 10);
            nextBeat = age + interval;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        BountyAlertPayload alert = current;
        Minecraft mc = Minecraft.getInstance();
        if (alert == null || !started || worldHidden(mc)) {
            return;
        }
        boolean paused = mc.screen != null;
        float t = age + (paused ? 0 : delta.getGameTimeDeltaPartialTick(false));
        float fadeIn = Mth.clamp(t / 6f, 0, 1);
        float fadeOut = Mth.clamp((duration - t) / 25f, 0, 1);
        float strength = fadeIn * fadeOut;
        // Heartbeat pulse: sharp attack, slow decay, faster over time. Under a menu it keeps beating slowly.
        float clock = paused ? (System.currentTimeMillis() % 100_000L) / 50f : t;
        float period = paused ? 20 : Math.max(7, 18 - t / 10f);
        float phase = (clock % period) / period;
        float pulse = phase < 0.15f ? phase / 0.15f : 1 - (phase - 0.15f) / 0.85f;

        int w = g.guiWidth(), h = g.guiHeight();
        // Screens since 1.21 blur the world behind them and leave the depth state such that plain fills would be
        // discarded. Draw on top of everything with our own, explicit state.
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        if (paused) {
            // A menu is open: only a thin pulsing rim, never over the menu itself.
            vignette(g, w, h, Math.max(0.35f, strength) * (0.45f + 0.35f * pulse), (int) (Math.min(w, h) * 0.06f));
        } else {
            g.fill(0, 0, w, h, ((int) (strength * (30 + 30 * pulse)) << 24) | 0x500000);
            vignette(g, w, h, strength * (0.75f + 0.35f * pulse), (int) (Math.min(w, h) * (0.22f + 0.08f * pulse)));
            banner(g, mc.font, alert, t, strength, pulse, w, h);
        }
        g.pose().popPose();
        g.flush();
        RenderSystem.enableDepthTest();
    }

    /** Dark red edges, {@code size} pixels deep. */
    private static void vignette(GuiGraphics g, int w, int h, float strength, int size) {
        for (int i = 0; i < size; i += 2) {
            float k = 1 - (float) i / size;
            int a = Mth.clamp((int) (strength * 200 * k * k), 0, 255);
            int c = (a << 24) | 0x6A0000;
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
    }

    /**
     * The centered banner: a blood ribbon that opens vertically, the title slamming down onto it, then the sponsor,
     * the number of bounties and the rewards on a row of slots.
     */
    private static void banner(GuiGraphics g, Font font, BountyAlertPayload alert, float t, float strength, float pulse, int w, int h) {
        float open = Mth.clamp(t / 8f, 0, 1);
        open = 1 - (1 - open) * (1 - open);
        int cy = h / 2 - 12;
        int half = (int) (44 * open * Mth.clamp(strength * 1.6f, 0, 1));
        if (half > 0) {
            // ribbon, darker and more transparent towards the sides
            for (int x = 0; x < w; x += 4) {
                float side = Math.abs(x + 2 - w / 2f) / (w / 2f);
                g.fill(x, cy - half, x + 4, cy + half, argb(strength * 0.85f * (1 - side * side * 0.85f), 0x1A0303));
            }
            int line = argb(strength * (0.7f + 0.3f * pulse), 0x9A1010);
            g.fill(0, cy - half - 1, w, cy - half + 1, line);
            g.fill(0, cy + half - 1, w, cy + half + 1, line);
            // blood running down from the top edge
            for (int d = 0; d < 9; d++) {
                int dx = (int) (w * (0.08f + d * 0.105f)) + (d * 37 % 13);
                int len = (int) (Mth.clamp((t - 4 - d) / 30f, 0, 1) * (6 + (d * 53 % 17)));
                g.fill(dx, cy - half + 1, dx + 2, cy - half + 1 + len, argb(strength * 0.9f, 0x8E0E0E));
                g.fill(dx, cy - half + 1 + len, dx + 2, cy - half + 3 + len, argb(strength * 0.9f, 0x5E0606));
            }
        }
        if (open < 1) {
            return;
        }
        // Title: falls from a huge size and lands with a shake.
        Component title = Component.translatable(alert.onLogin() ? "bloodcontracts.alert.title_login" : "bloodcontracts.alert.title");
        float land = Mth.clamp((t - 8) / 6f, 0, 1);
        float impact = land < 1 ? 0 : Math.max(0, 1 - (t - 14) / 10f);
        float shake = strength * ((alert.onLogin() ? 0.6f : 1.4f) * (0.4f + pulse) + impact * 4f);
        float sx = (SHAKE.nextFloat() - 0.5f) * 2 * shake, sy = (SHAKE.nextFloat() - 0.5f) * 2 * shake;
        int tw = font.width(title);
        // never wider than the screen on narrow windows
        float scale = Math.min(Mth.lerp(land, 7f, 2.6f) + 0.15f * pulse, (w - 20f) / tw);
        boolean flicker = t < 22 && ((int) t / 2) % 2 == 0;
        float textAlpha = Math.max(0.02f, strength * land);
        g.pose().pushPose();
        g.pose().translate(w / 2f + sx, cy - 30 + sy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, title, -tw / 2 + 1, 1, argb(textAlpha, 0x200000), false);
        g.drawString(font, title, -tw / 2, 0, argb(textAlpha, flicker ? 0xFFE0D0 : 0xE01818), false);
        g.pose().popPose();

        float rest = Mth.clamp((t - 16) / 8f, 0, 1);
        if (rest <= 0) {
            return;
        }
        float restAlpha = Math.max(0.02f, strength * rest);
        int dividerY = cy - 4;
        int dw = (int) (110 * rest);
        g.fill(w / 2 - dw, dividerY, w / 2 - 10, dividerY + 1, argb(restAlpha, 0xA06040));
        g.fill(w / 2 + 10, dividerY, w / 2 + dw, dividerY + 1, argb(restAlpha, 0xA06040));
        g.setColor(1, 1, 1, restAlpha);
        BCGui.drop(g, w / 2 - 8, dividerY - 8);
        g.setColor(1, 1, 1, 1);

        Component line = alert.onLogin()
                ? Component.translatable("bloodcontracts.alert.login", alert.bounties())
                : alert.sponsorName().map(n -> (Component) Component.translatable("bloodcontracts.alert.by", n))
                        .orElse(Component.translatable("bloodcontracts.alert.by_unknown"));
        g.drawString(font, line, w / 2 - font.width(line) / 2, cy + 8, argb(restAlpha, 0xF0D8B8), true);
        if (!alert.onLogin() && alert.bounties() > 1) {
            Component count = Component.translatable("bloodcontracts.alert.count", alert.bounties());
            g.drawString(font, count, w / 2 - font.width(count) / 2, cy + 20, argb(restAlpha, 0xFF5555), true);
        }

        List<ItemStack> rewards = alert.rewards();
        int shown = Math.min(rewards.size(), 9);
        if (shown > 0 && rest >= 1 && half > 0) {
            int rx = w / 2 - shown * 10;
            int ry = cy + half + 6;
            for (int i = 0; i < shown; i++) {
                BCGui.slot(g, rx + i * 20, ry, true);
                g.renderItem(rewards.get(i), rx + i * 20 + 1, ry + 1);
                g.renderItemDecorations(font, rewards.get(i), rx + i * 20 + 1, ry + 1);
            }
        }
    }
}
