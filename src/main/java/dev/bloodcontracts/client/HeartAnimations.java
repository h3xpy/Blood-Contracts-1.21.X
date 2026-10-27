package dev.bloodcontracts.client;

import com.mojang.math.Axis;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.BountyManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Heart gained or lost through a bounty, animated on the health bar. A gained heart appears large above the hotbar,
 * glows, flies into its slot and lands with a flash (the "heart container" moment); a lost heart shakes, turns white
 * and breaks in two halves that fall away. Half hearts given back by the progressive return get a short version.
 * <p>
 * Purely visual: it watches the {@code bloodcontracts:heart_balance} modifier the server syncs on {@code MAX_HEALTH}.
 */
public final class HeartAnimations {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "heart_animations");
    private static final ResourceLocation FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    /** Ticks to ignore changes after the local player entity is (re)created: respawn, dimension change, login. */
    private static final int SETTLE_TICKS = 20;

    private record Anim(boolean gain, boolean half, int slot, int duration, int[] age) {}

    private static final List<Anim> ANIMS = new ArrayList<>();
    private static int lastHp;
    private static int lastEntityId = -1;
    private static int settle;

    private HeartAnimations() {}

    private static int balanceHp(LocalPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier ours = max == null ? null : max.getModifier(BountyManager.HEART_MODIFIER);
        return ours == null ? 0 : (int) Math.round(ours.amount());
    }

    /** Client tick: detects balance changes and ages the running animations. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            lastEntityId = -1;
            ANIMS.clear();
            return;
        }
        int hp = balanceHp(player);
        if (player.getId() != lastEntityId) {
            lastEntityId = player.getId();
            settle = SETTLE_TICKS;
        }
        if (settle > 0) {
            settle--;
            lastHp = hp;
        } else if (hp != lastHp) {
            changed(mc, player, lastHp, hp);
            lastHp = hp;
        }
        for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext(); ) {
            Anim a = it.next();
            if (++a.age()[0] >= a.duration()) {
                it.remove();
            }
        }
    }

    private static void changed(Minecraft mc, LocalPlayer player, int before, int after) {
        int maxNow = Mth.ceil(player.getMaxHealth());
        int diff = after - before;
        // One animation per heart slot that changed (a slot holds HP 2s+1 and 2s+2), staggered so they do not pile up:
        // gains from the lowest slot up, losses from the highest down.
        int maxBefore = maxNow - diff;
        int low = Math.min(maxNow, maxBefore), high = Math.max(maxNow, maxBefore);
        int first = low / 2, last = (high + 1) / 2 - 1;
        int delay = 0;
        for (int i = 0; i <= last - first; i++) {
            int slot = diff > 0 ? first + i : last - i;
            boolean whole = low <= 2 * slot && high >= 2 * slot + 2;
            int duration = diff > 0 ? (whole ? 34 : 18) : (whole ? 30 : 16);
            ANIMS.add(new Anim(diff > 0, !whole, slot, duration, new int[] {-delay}));
            delay += whole ? 12 : 6;
        }
        var sounds = mc.getSoundManager();
        if (diff >= 2) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.5f));
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f));
        } else if (diff > 0) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.8f));
        } else {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, diff <= -2 ? 0.6f : 1.3f, diff <= -2 ? 0.7f : 0.35f));
            if (diff <= -2) {
                sounds.play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1.0f));
            }
        }
    }

    /** GUI layer drawn just above the vanilla health bar. */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (ANIMS.isEmpty() || mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        // Same layout as the vanilla health bar (Gui#renderPlayerHealth): rows of 10 hearts, squeezed when there are many.
        float max = mc.player.getMaxHealth();
        int rows = Mth.ceil((max + Mth.ceil(mc.player.getAbsorptionAmount())) / 2f / 10f);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int left = w / 2 - 91, top = h - 39;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (Anim a : ANIMS) {
            float t = a.age()[0] + partial;
            if (t < 0) {
                continue; // staggered: not started yet
            }
            float sx = left + (a.slot() % 10) * 8 + 4.5f, sy = top - (a.slot() / 10) * rowHeight + 4.5f;
            if (a.gain()) {
                gain(g, a, t, sx, sy, w / 2f, h - 64f);
            } else {
                loss(g, a, t, sx, sy);
            }
        }
        g.pose().popPose();
    }

    /** Draws a heart sprite centered on (x, y). */
    private static void heart(GuiGraphics g, ResourceLocation sprite, float x, float y, float scale, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.setColor(1, 1, 1, alpha);
        g.blitSprite(sprite, -4, -4, 9, 9);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
    }

    private static void glow(GuiGraphics g, float x, float y, float radius, float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        for (int r = (int) radius; r > 0; r -= 2) {
            int ra = (int) (a * (1 - r / radius) * 0.5f);
            g.fill((int) (x - r), (int) (y - r / 2f), (int) (x + r), (int) (y + r / 2f), (ra << 24) | rgb);
            g.fill((int) (x - r / 2f), (int) (y - r), (int) (x + r / 2f), (int) (y + r), (ra << 24) | rgb);
        }
    }

    private static void gain(GuiGraphics g, Anim a, float t, float slotX, float slotY, float cx, float cy) {
        ResourceLocation sprite = a.half() ? HALF : FULL;
        if (a.duration() < 30) {
            // half heart: a quick pop in place
            float k = t / a.duration();
            float scale = k < 0.4f ? Mth.lerp(k / 0.4f, 0.2f, 2.0f) : Mth.lerp((k - 0.4f) / 0.6f, 2.0f, 1.0f);
            glow(g, slotX, slotY, 10 * (1 - k), 1 - k, 0xFFF0C0);
            heart(g, sprite, slotX, slotY, scale, 1);
            return;
        }
        if (t < 12) {
            // appears above the hotbar and swells, with a growing glow and rotating rays
            float k = t / 12f;
            float scale = Mth.lerp(1 - (1 - k) * (1 - k), 0.2f, 3.2f);
            glow(g, cx, cy, 26 * k, k, 0xFFE8A0);
            rays(g, cx, cy, 18 + 10 * k, t * 6, k);
            heart(g, CONTAINER, cx, cy, scale, 1);
            heart(g, sprite, cx, cy, scale, 1);
        } else if (t < 24) {
            // flies into its slot along a little arc, shrinking
            float k = (t - 12) / 12f;
            float e = k * k * (3 - 2 * k);
            float x = Mth.lerp(e, cx, slotX), y = Mth.lerp(e, cy, slotY) - 14 * (float) Math.sin(Math.PI * e);
            glow(g, x, y, 18 * (1 - e) + 6, 1 - 0.5f * e, 0xFFE8A0);
            heart(g, CONTAINER, x, y, Mth.lerp(e, 3.2f, 1.6f), 1);
            heart(g, sprite, x, y, Mth.lerp(e, 3.2f, 1.6f), 1);
        } else {
            // lands: pop, white flash and sparkles
            float k = (t - 24) / (a.duration() - 24f);
            heart(g, sprite, slotX, slotY, Mth.lerp(k, 1.6f, 1.0f), 1);
            int flash = Mth.clamp((int) ((1 - k) * 220), 0, 255);
            heart(g, CONTAINER, slotX, slotY, 1 + 1.5f * k, (1 - k));
            g.fill((int) slotX - 4, (int) slotY - 4, (int) slotX + 5, (int) slotY + 5, (flash / 2 << 24) | 0xFFFFFF);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + 0.3;
                float d = 4 + 14 * k;
                int px = (int) (slotX + Math.cos(angle) * d), py = (int) (slotY + Math.sin(angle) * d);
                g.fill(px, py, px + 2, py + 2, (flash << 24) | (i % 2 == 0 ? 0xFFF4C0 : 0xFF6060));
            }
        }
    }

    private static void rays(GuiGraphics g, float x, float y, float length, float degrees, float alpha) {
        int a = Mth.clamp((int) (alpha * 90), 0, 255);
        for (int i = 0; i < 6; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(degrees + i * 60));
            g.fillGradient(-1, -(int) length, 1, 0, (a << 24) | 0xFFF4C0, 0x00FFF4C0);
            g.pose().popPose();
        }
    }

    private static void loss(GuiGraphics g, Anim a, float t, float x, float y) {
        ResourceLocation sprite = a.half() ? HALF : FULL;
        if (a.duration() < 20) {
            // half heart: fades out, shrinking and darkening
            float k = t / a.duration();
            g.setColor(1 - 0.5f * k, 1 - 0.8f * k, 1 - 0.8f * k, 1);
            heart(g, sprite, x, y + 4 * k, 1 - 0.5f * k, 1 - k);
            g.setColor(1, 1, 1, 1);
            return;
        }
        if (t < 7) {
            // shakes and whitens
            float k = t / 7f;
            float shake = 1.5f * k;
            float dx = (float) Math.sin(t * 9) * shake, dy = (float) Math.cos(t * 7) * shake;
            heart(g, CONTAINER, x + dx, y + dy, 1.3f, 1);
            heart(g, sprite, x + dx, y + dy, 1.3f, 1);
            g.fill((int) (x + dx) - 5, (int) (y + dy) - 5, (int) (x + dx) + 6, (int) (y + dy) + 6, ((int) (k * 140) << 24) | 0xFFFFFF);
            return;
        }
        // breaks in two halves that fall away, spinning, with a few red shards
        float k = (t - 7) / (a.duration() - 7f);
        float fall = 30 * k * k;
        float alpha = 1 - k;
        for (int side = -1; side <= 1; side += 2) {
            g.pose().pushPose();
            g.pose().translate(x + side * (2 + 10 * k), y - 6 * k + fall, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(side * 70 * k));
            g.pose().scale(side * 1.3f, 1.3f, 1); // the right half is the left half, mirrored
            g.setColor(1, 1, 1, alpha);
            g.blitSprite(HALF, -4, -4, 9, 9);
            g.setColor(1, 1, 1, 1);
            g.pose().popPose();
        }
        int shard = Mth.clamp((int) (alpha * 255), 0, 255);
        for (int i = 0; i < 6; i++) {
            float px = x + (i - 2.5f) * 4 * (0.5f + k), py = y + fall * (0.6f + i % 3 * 0.25f) - 4 * (float) Math.sin(i + k * 3);
            g.fill((int) px, (int) py, (int) px + 1, (int) py + 1, (shard << 24) | 0xB01010);
        }
    }
}
