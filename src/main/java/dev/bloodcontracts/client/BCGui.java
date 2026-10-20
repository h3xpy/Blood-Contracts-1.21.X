package dev.bloodcontracts.client;

import com.mojang.math.Axis;
import dev.bloodcontracts.BloodContracts;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** "Wanted poster" look shared by the Blood Contracts screens: parchment, dark wood frame, blood-red ink. */
final class BCGui {
    static final ResourceLocation PARCHMENT = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "textures/gui/parchment.png");
    static final ResourceLocation DROP = ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "textures/gui/blood_drop.png");

    static final int INK = 0xFF3B2412;
    static final int INK_LIGHT = 0xFF6B4A2A;
    static final int BLOOD = 0xFF8E0E0E;
    static final int BLOOD_BRIGHT = 0xFFC01818;
    static final int AMBER = 0xFF7A4A00;
    static final int SUCCESS = 0xFF2E6B1E;

    private static final int WOOD_DARK = 0xFF2A180B;
    private static final int WOOD = 0xFF5A3A1E;
    private static final int WOOD_LIGHT = 0xFF7A5430;

    private BCGui() {}

    /** Framed parchment panel with burnt edges. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // drop shadow
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000);
        // wooden frame
        g.fill(x, y, x + w, y + h, WOOD_DARK);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, WOOD_LIGHT, WOOD);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, WOOD_DARK);
        parchment(g, x + 5, y + 5, w - 10, h - 10);
        // burnt edges
        int ix = x + 5, iy = y + 5, iw = w - 10, ih = h - 10;
        g.fillGradient(ix, iy, ix + iw, iy + 8, 0x70402010, 0x00402010);
        g.fillGradient(ix, iy + ih - 8, ix + iw, iy + ih, 0x00402010, 0x70402010);
        for (int i = 0; i < 6; i++) {
            int alpha = (0x60 - i * 0x10) << 24;
            g.fill(ix + i, iy, ix + i + 1, iy + ih, alpha | 0x402010);
            g.fill(ix + iw - i - 1, iy, ix + iw - i, iy + ih, alpha | 0x402010);
        }
        // nails
        for (int[] c : new int[][] {{x + 2, y + 2}, {x + w - 4, y + 2}, {x + 2, y + h - 4}, {x + w - 4, y + h - 4}}) {
            g.fill(c[0], c[1], c[0] + 2, c[1] + 2, 0xFFB8B0A0);
        }
    }

    /** Tiles the parchment texture over an area. */
    static void parchment(GuiGraphics g, int x, int y, int w, int h) {
        for (int ty = 0; ty < h; ty += 256) {
            for (int tx = 0; tx < w; tx += 256) {
                g.blit(PARCHMENT, x + tx, y + ty, tx % 256, ty % 256, Math.min(256, w - tx), Math.min(256, h - ty), 256, 256);
            }
        }
    }

    /** A darker, inked frame on the parchment (cards, insets). */
    static void inset(GuiGraphics g, int x, int y, int w, int h, boolean highlighted) {
        g.fill(x, y, x + w, y + h, highlighted ? 0x408E0E0E : 0x203B2412);
        g.renderOutline(x, y, w, h, highlighted ? BLOOD : 0x803B2412);
    }

    /** An 18x18 slot frame whose inner 16x16 starts at (x+1, y+1). */
    static void slot(GuiGraphics g, int x, int y, boolean accent) {
        g.fill(x, y, x + 18, y + 18, accent ? BLOOD : 0xFF4A3A28);
        g.fill(x + 1, y + 1, x + 18, y + 18, accent ? 0xFFE08080 : 0xFFE8D8B4);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B7355);
    }

    /** Horizontal divider with a blood drop in the middle. */
    static void divider(GuiGraphics g, int x1, int x2, int y) {
        int mid = (x1 + x2) / 2;
        g.fill(x1, y, mid - 10, y + 1, 0xA03B2412);
        g.fill(mid + 10, y, x2, y + 1, 0xA03B2412);
        g.blit(DROP, mid - 8, y - 8, 0, 0, 16, 16, 16, 16);
    }

    /** Plain inked rule. */
    static void rule(GuiGraphics g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, 0xA03B2412);
        g.fill(x1 + 1, y + 1, x2 + 1, y + 2, 0x30FFFFFF);
    }

    static void drop(GuiGraphics g, int x, int y) {
        g.blit(DROP, x, y, 0, 0, 16, 16, 16, 16);
    }

    /** Title text, scaled, centered, in blood red with a darker offset instead of the bright vanilla shadow. */
    static void title(GuiGraphics g, Font font, Component text, int cx, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(text);
        g.drawString(font, text, -w / 2 + 1, 1, 0x60200000, false);
        g.drawString(font, text, -w / 2, 0, BLOOD, false);
        g.pose().popPose();
    }

    static void centered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** A tilted rubber stamp ("PAID", "CANCELLED"). */
    static void stamp(GuiGraphics g, Font font, Component text, int cx, int cy, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 200);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-12));
        int w = font.width(text);
        int border = (color & 0x00FFFFFF) | 0xB0000000;
        g.renderOutline(-w / 2 - 4, -6, w + 8, 13, border);
        g.renderOutline(-w / 2 - 3, -5, w + 6, 11, border);
        g.drawString(font, text, -w / 2, -3, (color & 0x00FFFFFF) | 0xC0000000, false);
        g.pose().popPose();
    }

    /** Draws a player's face with their real skin, online or not (see {@link ClientSkins}). */
    static void face(GuiGraphics g, UUID player, int x, int y, int size) {
        PlayerSkin skin = ClientSkins.get(player);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, WOOD_DARK);
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    /** Vertical scrollbar; {@code first} is the index of the first visible row. */
    static void scrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int first) {
        if (total <= visible) {
            return;
        }
        g.fill(x, y, x + 4, y + h, 0x403B2412);
        int thumb = Math.max(12, h * visible / total);
        int top = y + (h - thumb) * first / Math.max(1, total - visible);
        g.fill(x, top, x + 4, top + thumb, INK_LIGHT);
        g.fill(x, top, x + 3, top + thumb - 1, 0xFF8B6A3E);
    }

    /** Button drawn as a dark-red wax/leather plate instead of the grey vanilla button. */
    static class BloodButton extends Button {
        private final boolean primary;

        BloodButton(int x, int y, int w, int h, Component message, OnPress onPress, boolean primary) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int top, bottom, text;
            if (!active) {
                top = 0xFF8A7A62;
                bottom = 0xFF6E604C;
                text = 0xFFC8B898;
            } else if (primary) {
                top = isHoveredOrFocused() ? 0xFFC42424 : 0xFFA01818;
                bottom = isHoveredOrFocused() ? 0xFF8E0E0E : 0xFF6A0808;
                text = 0xFFF4E6C8;
            } else {
                top = isHoveredOrFocused() ? 0xFF8A6034 : 0xFF6E4A26;
                bottom = isHoveredOrFocused() ? 0xFF5E3C1C : 0xFF4A2E14;
                text = 0xFFF4E6C8;
            }
            g.fill(x, y, x + w, y + h, WOOD_DARK);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
            Font font = Minecraft.getInstance().font;
            Component msg = getMessage();
            g.drawString(font, msg, x + (w - font.width(msg)) / 2, y + (h - 8) / 2, text, active);
        }
    }

    /** Tab drawn as a parchment label: the selected tab merges into the page, the other one is darker. */
    static class TabButton extends Button {
        boolean selected;
        /** Something waits behind this tab: pulsing blood-drop badge. */
        boolean attention;

        TabButton(int x, int y, int w, int h, Component message, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0xFF3B2412);
            if (selected) {
                parchment(g, x + 1, y + 1, w - 2, h - 1);
                g.fill(x + 1, y + 1, x + w - 1, y + 3, BLOOD);
            } else {
                g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, isHoveredOrFocused() ? 0xFFB09870 : 0xFF9C8460, 0xFF7E6846);
            }
            Font font = Minecraft.getInstance().font;
            Component msg = getMessage();
            g.drawString(font, msg, x + (w - font.width(msg)) / 2, y + (h - 8) / 2 + 1, selected ? BLOOD : INK, false);
            if (attention) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0));
                g.pose().pushPose();
                g.pose().translate(x + w - 9, y - 5, 0);
                float scale = 0.75f + 0.2f * pulse;
                g.pose().scale(scale, scale, 1);
                drop(g, -8, -2);
                g.pose().popPose();
            }
        }
    }
}
