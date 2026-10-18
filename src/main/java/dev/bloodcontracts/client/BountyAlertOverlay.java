package dev.bloodcontracts.client;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import java.util.List;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * "There is a price on your head": a full-screen, deliberately stressful alert (not a chat message). Pulsing blood-red
 * vignette, shaking title, a curse sting then an accelerating heartbeat. Drawn as a GUI layer above everything, so it
 * shows over the game and over open screens alike.
 */
public final class BountyAlertOverlay {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "bounty_alert");
    private static final int DURATION = 140;
    private static final int LOGIN_DURATION = 100;

    private static @Nullable BountyAlertPayload current;
    private static int age;
    private static int duration;
    private static int nextBeat;
    private static final Random SHAKE = new Random();

    private BountyAlertOverlay() {}

    public static void trigger(BountyAlertPayload alert) {
        current = alert;
        age = 0;
        duration = alert.onLogin() ? LOGIN_DURATION : DURATION;
        nextBeat = 6;
        var sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.9f));
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.5f, 1.0f));
    }

    /** Client tick: heartbeat that speeds up while the alert is on screen. */
    public static void tick() {
        if (current == null) {
            return;
        }
        age++;
        if (age >= duration) {
            current = null;
            return;
        }
        if (age >= nextBeat && age < duration - 20) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0f + age / 300f, 1.0f));
            int interval = Math.max(7, 18 - age / 10);
            nextBeat = age + interval;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        BountyAlertPayload alert = current;
        Minecraft mc = Minecraft.getInstance();
        if (alert == null || mc.options.hideGui) {
            return;
        }
        float t = age + delta.getGameTimeDeltaPartialTick(false);
        float fadeIn = Mth.clamp(t / 6f, 0, 1);
        float fadeOut = Mth.clamp((duration - t) / 25f, 0, 1);
        float strength = fadeIn * fadeOut;
        // Heartbeat pulse: sharp attack, slow decay, faster over time.
        float period = Math.max(7, 18 - t / 10f);
        float phase = (t % period) / period;
        float pulse = phase < 0.15f ? phase / 0.15f : 1 - (phase - 0.15f) / 0.85f;

        int w = g.guiWidth(), h = g.guiHeight();
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);

        // Blood tint and pulsing vignette.
        int tint = (int) (strength * (40 + 40 * pulse));
        g.fill(0, 0, w, h, (tint << 24) | 0x500000);
        int edge = (int) (Math.min(w, h) * (0.22f + 0.08f * pulse));
        for (int i = 0; i < edge; i += 2) {
            float k = 1 - (float) i / edge;
            int a = (int) (strength * (150 + 80 * pulse) * k * k);
            int c = (Mth.clamp(a, 0, 255) << 24) | 0x6A0000;
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }

        Font font = mc.font;
        Component title = Component.translatable(alert.onLogin() ? "bloodcontracts.alert.title_login" : "bloodcontracts.alert.title");
        float shake = strength * (alert.onLogin() ? 1.0f : 2.5f) * (0.5f + pulse);
        float sx = (SHAKE.nextFloat() - 0.5f) * 2 * shake, sy = (SHAKE.nextFloat() - 0.5f) * 2 * shake;
        float scale = 3.0f + 0.25f * pulse;
        int textAlpha = Mth.clamp((int) (255 * strength), 5, 255);
        boolean flicker = t < 20 && ((int) t / 2) % 2 == 0;
        g.pose().pushPose();
        g.pose().translate(w / 2f + sx, h / 2f - 34 + sy, 0);
        g.pose().scale(scale, scale, 1);
        int tw = font.width(title);
        g.drawString(font, title, -tw / 2 + 1, 1, (textAlpha << 24), false);
        g.drawString(font, title, -tw / 2, 0, (textAlpha << 24) | (flicker ? 0xFFFFFF : 0xE01010), false);
        g.pose().popPose();

        Component line = alert.onLogin()
                ? Component.translatable("bloodcontracts.alert.login", alert.bounties())
                : alert.sponsorName().map(n -> (Component) Component.translatable("bloodcontracts.alert.by", n))
                        .orElse(Component.translatable("bloodcontracts.alert.by_unknown"));
        int lineColor = (textAlpha << 24) | 0xF0D0B0;
        g.drawString(font, line, w / 2 - font.width(line) / 2, h / 2 - 4, lineColor, true);
        if (!alert.onLogin() && alert.bounties() > 1) {
            Component count = Component.translatable("bloodcontracts.alert.count", alert.bounties());
            g.drawString(font, count, w / 2 - font.width(count) / 2, h / 2 + 8, (textAlpha << 24) | 0xFF5555, true);
        }

        List<ItemStack> rewards = alert.rewards();
        int shown = Math.min(rewards.size(), 9);
        if (shown > 0 && strength > 0.3f) {
            int rx = w / 2 - shown * 10;
            int ry = h / 2 + 22;
            for (int i = 0; i < shown; i++) {
                g.renderItem(rewards.get(i), rx + i * 20, ry);
                g.renderItemDecorations(font, rewards.get(i), rx + i * 20, ry);
            }
        }
        g.pose().popPose();
    }
}
