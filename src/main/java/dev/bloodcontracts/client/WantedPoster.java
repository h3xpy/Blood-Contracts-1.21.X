package dev.bloodcontracts.client;

import com.mojang.math.Axis;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.network.BountyView;
import dev.bloodcontracts.network.TargetView;
import dev.bloodcontracts.util.Durations;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Western "wanted" poster, drawn in local coordinates {@code 0..W x 0..H} after {@link #begin}: aged torn paper, title,
 * the target's real face (with the hat layer), the rewards, and stamps (private, immune, collected, withdrawn).
 */
final class WantedPoster {
    static final int W = 70;
    static final int H = 94;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "textures/gui/poster.png");
    private static final int TEX_W = 128;
    private static final int TEX_H = 176;
    static final int PRIVATE_INK = 0xFF2E3A7A;
    static final int IMMUNE_INK = 0xFF1E6470;
    static final int GREY_INK = 0xFF505050;
    /** Above rendered items (which sit around z 150). */
    private static final float OVERLAY_Z = 260;

    private WantedPoster() {}

    /** Stable tilt for a target, in degrees: the same poster always hangs the same way. */
    static float tilt(UUID id) {
        long h = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits();
        return (Math.floorMod(h, 13) - 6) * 0.5f;
    }

    /** Pushes a pose centered on the poster at screen (x, y) with the given scale and tilt. Pair with {@code g.pose().popPose()}. */
    static void begin(GuiGraphics g, float x, float y, float scale, float tiltDegrees, float z) {
        g.pose().pushPose();
        g.pose().translate(x + W * scale / 2f, y + H * scale / 2f, z);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tiltDegrees));
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-W / 2f, -H / 2f, 0);
    }

    private static void paper(GuiGraphics g) {
        g.fill(2, 3, W + 2, H + 3, 0x50000000);
        g.blit(TEXTURE, 0, 0, W, H, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    static void text(GuiGraphics g, Font font, Component text, float cx, float y, float scale, int color, int maxWidth) {
        String s = font.plainSubstrByWidth(text.getString(), (int) (maxWidth / scale));
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, color, false);
        g.pose().popPose();
    }

    private static void header(GuiGraphics g, Font font) {
        text(g, font, Component.translatable("bloodcontracts.poster.wanted").withStyle(s -> s.withBold(true)), W / 2f, 9, 0.85f, BCGui.BLOOD, W - 6);
        g.fill(8, 17, W - 8, 18, 0x803B2412);
    }

    private static void portrait(GuiGraphics g, UUID target) {
        int size = 28, x = (W - size) / 2, y = 20;
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF3B2412);
        BCGui.face(g, target, x, y, size);
    }

    /** Overlays in poster space, drawn above the items. */
    private static void overlay(GuiGraphics g, Runnable draw) {
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        draw.run();
        g.pose().popPose();
    }

    private static void rewards(GuiGraphics g, Font font, List<ItemStack> stacks, int y) {
        if (stacks.size() == 1) {
            ItemStack stack = stacks.get(0);
            g.pose().pushPose();
            g.pose().translate(W / 2f - 12, y, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.renderItem(stack, 0, 0);
            g.renderItemDecorations(font, stack, 0, 0);
            g.pose().popPose();
            return;
        }
        int shown = Math.min(3, stacks.size());
        int x0 = (W - shown * 17) / 2 - (stacks.size() > 3 ? 5 : 0);
        for (int i = 0; i < shown; i++) {
            g.renderItem(stacks.get(i), x0 + i * 17, y + 2);
            g.renderItemDecorations(font, stacks.get(i), x0 + i * 17, y + 2);
        }
        if (stacks.size() > 3) {
            overlay(g, () -> text(g, font, Component.literal("+" + (stacks.size() - 3)), x0 + shown * 17 + 6, y + 7, 0.75f, BCGui.BLOOD, 20));
        }
    }

    /** "Immune" ribbon across the portrait. */
    private static void band(GuiGraphics g, Font font, Component text) {
        g.pose().pushPose();
        g.pose().translate(W / 2f, 40, OVERLAY_Z);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-10));
        g.fill(-W / 2 - 3, -5, W / 2 + 3, 5, 0xE01E6470);
        g.fill(-W / 2 - 3, -5, W / 2 + 3, -4, 0xFF5FB0BC);
        g.fill(-W / 2 - 3, 4, W / 2 + 3, 5, 0xFF0E3A40);
        text(g, font, text, 0, -2.5f, 0.6f, 0xFFEFFAFA, W);
        g.pose().popPose();
    }

    /** A stamp across the poster, in poster space. */
    private static void stamp(GuiGraphics g, Font font, Component text, float cx, float cy, float scale, float angle, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, OVERLAY_Z);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.pose().scale(scale, scale, 1);
        int w = font.width(text);
        int border = (color & 0x00FFFFFF) | 0xC0000000;
        g.renderOutline(-w / 2 - 4, -6, w + 8, 13, border);
        g.renderOutline(-w / 2 - 3, -5, w + 6, 11, border);
        g.fill(-w / 2 - 2, -4, w / 2 + 2, 5, 0x30FFFFFF);
        g.drawString(font, text, -w / 2, -3, (color & 0x00FFFFFF) | 0xE0000000, false);
        g.pose().popPose();
    }

    /** An active poster: every bounty on the target. {@code now} is the server clock. */
    static void drawTarget(GuiGraphics g, Font font, TargetView t, long now) {
        paper(g);
        header(g, font);
        portrait(g, t.targetId());
        text(g, font, Component.literal(t.targetName()).withStyle(s -> s.withBold(true)), W / 2f, 51, 0.8f, BCGui.INK, W - 8);
        List<ItemStack> stacks = t.bounties().stream().flatMap(b -> b.rewards().stream()).toList();
        rewards(g, font, stacks, 57);
        overlay(g, () -> {
            if (t.bounties().size() > 1) {
                // "x3" badge in the corner: several bounties on this target.
                g.fill(4, 20, 18, 30, BCGui.BLOOD);
                g.renderOutline(4, 20, 14, 10, 0xFF3B0606);
                text(g, font, Component.literal("×" + t.bounties().size()), 11, 21.5f, 0.75f, 0xFFF4E6C8, 14);
            }
            long notice = t.bounties().stream().filter(b -> b.cancelAt().isPresent()).mapToLong(b -> b.cancelAt().get()).min().orElse(-1);
            if (notice > 0) {
                text(g, font, Component.translatable("bloodcontracts.poster.expires", upper(Durations.of(notice - now))),
                        W / 2f, 80, 0.6f, BCGui.BLOOD_BRIGHT, W - 6);
            }
            if (t.eligible()) {
                g.fill(6, 86, W - 6, 92, 0x602E6B1E);
                text(g, font, Component.translatable("bloodcontracts.poster.can_collect"), W / 2f, 86.5f, 0.55f, 0xFFE8F4D8, W - 10);
            }
        });
        if (t.bounties().stream().anyMatch(b -> b.type() == BountyType.PRIVATE)) {
            stamp(g, font, Component.translatable("bloodcontracts.poster.private"), W - 9, 34, 0.55f, 90, PRIVATE_INK);
        }
        t.immuneUntil().filter(until -> until > now).ifPresent(until -> band(g, font,
                Component.translatable("bloodcontracts.poster.immune", upper(Durations.of(until - now)))));
        if (!t.eligible()) {
            overlay(g, () -> g.fill(0, 0, W, H, 0x58706050));
        }
    }

    /** A closed bounty, stamped "collected by" or "withdrawn". */
    static void drawHistory(GuiGraphics g, Font font, BountyView v) {
        paper(g);
        header(g, font);
        portrait(g, v.targetId());
        text(g, font, Component.literal(v.targetName()).withStyle(s -> s.withBold(true)), W / 2f, 51, 0.8f, BCGui.INK, W - 8);
        rewards(g, font, v.rewards(), 57);
        if (v.type() == BountyType.PRIVATE) {
            stamp(g, font, Component.translatable("bloodcontracts.poster.private"), W - 9, 34, 0.55f, 90, PRIVATE_INK);
        }
        overlay(g, () -> g.fill(0, 0, W, H, 0x30403020));
        if (v.state() == BountyState.CLAIMED) {
            stamp(g, font, Component.translatable("bloodcontracts.poster.claimed_by", v.hunterName().orElse("?").toUpperCase(Locale.ROOT)),
                    W / 2f, 64, 0.55f, -16, BCGui.BLOOD);
        } else {
            stamp(g, font, Component.translatable("bloodcontracts.poster.withdrawn"), W / 2f, 64, 0.8f, -16, GREY_INK);
        }
    }

    static Component upper(Component c) {
        return Component.literal(c.getString().toUpperCase(Locale.ROOT));
    }
}
