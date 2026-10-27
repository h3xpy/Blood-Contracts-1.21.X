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
 * A change waits until the player can see their health bar: a heart lost on death plays after the respawn, one changed
 * while a menu is open plays once it is closed.
 */
public final class HeartAnimations {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "heart_animations");
    private static final ResourceLocation FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    /** Ticks to wait for the attributes of a (re)created local player entity: respawn, dimension change, login. */
    private static final int SETTLE_TICKS = 20;
    /** Ticks a change must be seen with the player alive and in game before it is animated. */
    private static final int CONFIRM_TICKS = 5;

    /**
     * @param fill how much of the slot is red (0 empty container, 1 half, 2 full): before the loss, or after the gain
     * @param hold extra ticks a lost heart stays on its slot before breaking (several losses break one after the other,
     *             but all cover their slot from the start)
     */
    private record Anim(boolean gain, boolean half, int slot, int fill, int hold, int duration, int[] age) {}

    /** The vanilla bar keeps showing a lost heart slot (as its "display health") for about a second. */
    private static final int VANILLA_LAG = 20;

    private static final List<Anim> ANIMS = new ArrayList<>();
    private static int lastHp;
    private static float lastHealth;
    private static boolean hasBaseline;
    private static int readyTicks;
    /** The player entity was recreated since the baseline (a respawn): a lost heart is shown as it was, full. */
    private static boolean respawned;
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
            hasBaseline = false;
            respawned = false;
            ANIMS.clear();
            return;
        }
        int hp = balanceHp(player);
        if (player.getId() != lastEntityId) {
            // New player entity (login, respawn, dimension change): its attributes arrive a moment later. The baseline
            // is kept across respawns, so a heart lost on death is still shown once the player is back.
            if (lastEntityId != -1 && hasBaseline) {
                respawned = true;
            }
            lastEntityId = player.getId();
            settle = SETTLE_TICKS;
        }
        if (settle > 0) {
            settle--;
        }
        // Changes wait until the player can see their health bar: alive, back in the game (no death screen, no menu).
        // A change must also hold for a few such ticks: on death the new maximum can arrive a tick before the death
        // itself, and the animation would then run behind the death screen.
        boolean ready = settle == 0 && player.isAlive() && mc.screen == null;
        readyTicks = ready ? readyTicks + 1 : 0;
        if (ready) {
            if (!hasBaseline) {
                lastHp = hp; // just joined: what they have is not news
                hasBaseline = true;
            } else if (hp != lastHp && readyTicks >= CONFIRM_TICKS) {
                changed(mc, player, lastHp, hp);
                lastHp = hp;
                respawned = false;
            }
            if (hp == lastHp) {
                respawned = false;
                lastHealth = player.getHealth();
            }
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
            // red part of the slot as the bar shows it: after a gain, the current health; before a loss, last tick's
            float health = diff > 0 ? player.getHealth() : respawned ? maxBefore : lastHealth;
            int fill = Mth.clamp(Mth.ceil(health) - 2 * slot, 0, 2);
            int hold = diff > 0 ? 0 : delay;
            int duration = diff > 0 ? (whole ? 34 : 18) : (whole ? VANILLA_LAG + hold + 22 : 16 + hold);
            ANIMS.add(new Anim(diff > 0, !whole, slot, fill, hold, duration, new int[] {diff > 0 ? -delay : 0}));
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
        if (ANIMS.isEmpty() || mc.player == null || !mc.player.isAlive() || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
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

    /** Small sparkles flying out of (x, y); {@code k} goes 0 to 1. */
    private static void sparkles(GuiGraphics g, float x, float y, float k, int count, float reach) {
        int alpha = Mth.clamp((int) ((1 - k) * 255), 0, 255);
        for (int i = 0; i < count; i++) {
            double angle = i * 2 * Math.PI / count + 0.4;
            float d = 3 + reach * k;
            int px = Math.round(x + (float) Math.cos(angle) * d), py = Math.round(y + (float) Math.sin(angle) * d);
            g.fill(px, py, px + 1, py + 1, (alpha << 24) | (i % 2 == 0 ? 0xFFF4C0 : 0xFF7070));
        }
    }

    /**
     * Gained heart. Whole heart: it appears above the hotbar, flies to its slot, lands exactly on it with a small bounce,
     * then its red fades so the slot is left as the vanilla bar shows it (a new, still empty heart container).
     * Half heart: a quick pop on the slot.
     */
    private static void gain(GuiGraphics g, Anim a, float t, float slotX, float slotY, float cx, float cy) {
        ResourceLocation sprite = a.half() ? HALF : FULL;
        if (a.half()) {
            float k = t / a.duration();
            float scale = k < 0.35f ? Mth.lerp(k / 0.35f, 0.4f, 1.8f) : Mth.lerp((k - 0.35f) / 0.65f, 1.8f, 1.0f);
            heart(g, sprite, slotX, slotY, scale, k < 0.7f ? 1 : (1 - k) / 0.3f);
            sparkles(g, slotX, slotY, k, 6, 8);
            return;
        }
        if (t < 12) {
            // appears and swells above the hotbar, with turning rays
            float k = t / 12f;
            float scale = Mth.lerp(1 - (1 - k) * (1 - k), 0.3f, 3.0f);
            rays(g, cx, cy, 10 + 14 * k, t * 6, k);
            heart(g, CONTAINER, cx, cy, scale, 1);
            heart(g, sprite, cx, cy, scale, 1);
        } else if (t < 24) {
            // flies into its slot along a little arc, shrinking to the slot's size
            float k = (t - 12) / 12f;
            float e = k * k * (3 - 2 * k);
            float x = Mth.lerp(e, cx, slotX), y = Mth.lerp(e, cy, slotY) - 14 * (float) Math.sin(Math.PI * e);
            float scale = Mth.lerp(e, 3.0f, 1.0f);
            heart(g, CONTAINER, x, y, scale, 1);
            heart(g, sprite, x, y, scale, 1);
        } else {
            // lands on the slot: bounce, sparkles, then the red drains away and only the container stays
            // lands on the slot: bounce and sparkles, then it settles to what the bar really shows (full, half, or a new
            // empty container when the player's health has not caught up yet)
            float k = (t - 24) / (a.duration() - 24f);
            float bounce = 1 + 0.35f * (float) Math.sin(Math.PI * Math.min(1, k * 2));
            float drain = k < 0.5f ? 1 : (1 - k) * 2;
            heart(g, CONTAINER, slotX, slotY, bounce, 1);
            if (a.fill() >= 2) {
                heart(g, FULL, slotX, slotY, bounce, 1);
            } else {
                if (a.fill() == 1) {
                    heart(g, HALF, slotX, slotY, bounce, 1);
                }
                heart(g, FULL, slotX, slotY, bounce, drain);
            }
            sparkles(g, slotX, slotY, k, 8, 12);
        }
    }

    private static void rays(GuiGraphics g, float x, float y, float length, float degrees, float alpha) {
        int a = Mth.clamp((int) (alpha * 110), 0, 255);
        for (int i = 0; i < 8; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(degrees + i * 45));
            g.fillGradient(0, -(int) length, 1, -6, 0x00FFF4C0, (a << 24) | 0xFFF4C0);
            g.pose().popPose();
        }
    }

    /**
     * Lost heart. Whole heart: on its slot (which the vanilla bar no longer draws), it pales and trembles, then breaks
     * in two halves that fall away; nothing stays behind. Half heart: it pales, shrinks and fades.
     */
    private static void loss(GuiGraphics g, Anim a, float t, float x, float y) {
        ResourceLocation sprite = a.half() ? HALF : FULL;
        if (a.half()) {
            float k = t / a.duration();
            g.setColor(1, 1 - 0.4f * k, 1 - 0.4f * k, 1);
            heart(g, sprite, x, y + 3 * k, 1 - 0.4f * k, 1 - k);
            g.setColor(1, 1, 1, 1);
            return;
        }
        ResourceLocation content = a.fill() >= 2 ? FULL : a.fill() == 1 ? HALF : null;
        if (t < VANILLA_LAG + a.hold()) {
            // Sits exactly on its slot (where the vanilla bar still draws an empty container), drains and, at the end,
            // trembles by one pixel.
            float k = Math.min(1, t / VANILLA_LAG);
            float dx = k > 0.6f ? Math.round(Math.sin(t * 9)) : 0;
            heart(g, CONTAINER, x + dx, y, 1, 1);
            if (content != null) {
                g.setColor(1, 1 - 0.6f * k, 1 - 0.6f * k, 1);
                heart(g, content, x + dx, y, 1, 1);
                g.setColor(1, 1, 1, 1);
            }
            return;
        }
        // The slot is gone from the bar: the heart breaks in two halves that spin and fall away; nothing stays behind.
        float k = (t - VANILLA_LAG - a.hold()) / (a.duration() - VANILLA_LAG - a.hold());
        float fall = 26 * k * k;
        float alpha = 1 - k;
        for (int side = -1; side <= 1; side += 2) {
            if (content == HALF && side == 1) {
                continue; // only a left half was red
            }
            g.pose().pushPose();
            g.pose().translate(x + side * (1 + 9 * k), y - 5 * k + fall, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(side * 80 * k));
            g.pose().scale(side, 1, 1); // the right half is the left half, mirrored
            if (content == null) {
                g.setColor(0.4f, 0.4f, 0.4f, alpha);
            } else {
                g.setColor(0.6f, 0.4f, 0.4f, alpha);
            }
            g.blitSprite(content == null ? CONTAINER : HALF, -4, -4, 9, 9);
            g.setColor(1, 1, 1, 1);
            g.pose().popPose();
            if (content == null) {
                break; // an empty container just falls
            }
        }
        int shard = Mth.clamp((int) (alpha * 255), 0, 255);
        for (int i = 0; i < 6; i++) {
            float px = x + (i - 2.5f) * 3 * (0.5f + 1.5f * k), py = y + fall * (0.5f + (i % 3) * 0.3f) - 3 * (float) Math.sin(i + k * 4);
            g.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 1, (shard << 24) | 0x9A1010);
        }
    }
}
