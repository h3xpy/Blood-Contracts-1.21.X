package dev.bloodcontracts.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** "Rewards to claim" toast, in the bounty colours, pointing to the board instead of filling the inventory. */
public class RewardToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_MS = 6000;

    private final int count;

    public RewardToast(int count) {
        this.count = count;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        BCGui.drop(g, 8, 8);
        var font = toasts.getMinecraft().font;
        g.drawString(font, Component.translatable("bloodcontracts.toast.rewards.title"), 30, 7, 0xFFFF5555, false);
        g.drawString(font, Component.translatable("bloodcontracts.toast.rewards.body", count), 30, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
