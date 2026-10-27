package dev.bloodcontracts.client;

import dev.bloodcontracts.network.BCPayloads;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Small notification in the mod's style (dark wood frame, parchment, blood-red ink): rewards waiting in the board,
 * or the target of one of your bounties killed under contract.
 */
public final class BloodToast implements Toast {
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final int WIDTH = 200;
    private static final int HEIGHT = 34;
    private static final long DISPLAY_MS = 6000;

    private final Component title;
    private final Component body;
    private final ItemStack icon;
    private final @Nullable UUID face;
    private final boolean heart;
    private boolean soundPlayed;

    private BloodToast(Component title, Component body, ItemStack icon, @Nullable UUID face, boolean heart) {
        this.title = title;
        this.body = body;
        this.icon = icon;
        this.face = face;
        this.heart = heart;
    }

    /** {@code count} stacks wait in the "to claim" tab. */
    public static BloodToast rewards(int count) {
        return new BloodToast(Component.translatable("bloodcontracts.toast.rewards.title"),
                Component.translatable("bloodcontracts.toast.rewards.body", count), new ItemStack(Items.CHEST), null, false);
    }

    /** The target of one of your bounties was killed under contract. */
    public static BloodToast targetDown(BCPayloads.TargetDownPayload p) {
        return new BloodToast(Component.translatable("bloodcontracts.toast.target_down.title"),
                Component.translatable("bloodcontracts.toast.target_down.body", p.hunterName(), p.targetName()),
                ItemStack.EMPTY, p.target(), p.heartGained());
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        if (!soundPlayed) {
            soundPlayed = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    face != null ? SoundEvents.BELL_BLOCK : SoundEvents.ARMOR_EQUIP_CHAIN.value(), face != null ? 0.7f : 1.2f, 0.6f));
        }
        // frame: dark wood, lighter bevel, parchment
        g.fill(0, 0, WIDTH, HEIGHT, 0xFF2A180B);
        g.fillGradient(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF7A5430, 0xFF5A3A1E);
        g.fill(3, 3, WIDTH - 3, HEIGHT - 3, 0xFF2A180B);
        BCGui.parchment(g, 4, 4, WIDTH - 8, HEIGHT - 8);
        g.fill(4, 4, 6, HEIGHT - 4, BCGui.BLOOD);
        // icon or target face in a slot
        BCGui.slot(g, 8, 8, true);
        if (face != null) {
            BCGui.face(g, face, 9, 9, 16);
        } else {
            g.renderItem(icon, 9, 9);
        }
        Font font = toasts.getMinecraft().font;
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), 31, 8, BCGui.BLOOD, false);
        int bodyWidth = WIDTH - 52;
        g.drawString(font, font.plainSubstrByWidth(body.getString(), bodyWidth), 31, 19, BCGui.INK, false);
        if (heart) {
            int hx = WIDTH - 26;
            // the heart gained, gently beating
            float beat = 1 + 0.15f * (float) Math.max(0, Math.sin(timeSinceLastVisible / 140.0));
            g.pose().pushPose();
            g.pose().translate(hx + 4.5f, 12.5f, 0);
            g.pose().scale(beat, beat, 1);
            g.blitSprite(HEART, -4, -4, 9, 9);
            g.pose().popPose();
            g.drawString(font, "+1", hx + 10, 9, BCGui.BLOOD, false);
        }
        BCGui.drop(g, WIDTH - 19, HEIGHT - 18);
        return timeSinceLastVisible >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
