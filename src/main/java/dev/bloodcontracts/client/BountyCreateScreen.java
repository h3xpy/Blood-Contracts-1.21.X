package dev.bloodcontracts.client;

import com.mojang.authlib.GameProfile;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Bounty creation: pick a target among online players, put the reward in the (server-side) slot, confirm.
 * Only the target's UUID is sent; the server takes the reward from its own copy of the slot.
 */
public class BountyCreateScreen extends AbstractContainerScreen<BountyCreateMenu> {
    private List<GameProfile> candidates = List.of();
    private int index;
    private Button confirm;

    public BountyCreateScreen(BountyCreateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        refreshCandidates();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).bounds(leftPos + 8, topPos + 18, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).bounds(leftPos + 148, topPos + 18, 20, 18).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("bloodcontracts.gui.confirm"), b -> submit())
                .bounds(leftPos + 104, topPos + 52, 64, 20).build());
    }

    private void refreshCandidates() {
        if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }
        var self = minecraft.player.getUUID();
        candidates = minecraft.getConnection().getOnlinePlayers().stream()
                .map(PlayerInfo::getProfile)
                .filter(p -> !p.getId().equals(self))
                .sorted(Comparator.comparing(GameProfile::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        index = Math.min(index, Math.max(0, candidates.size() - 1));
    }

    private void cycle(int delta) {
        refreshCandidates();
        if (!candidates.isEmpty()) {
            index = Math.floorMod(index + delta, candidates.size());
        }
    }

    private void submit() {
        if (!candidates.isEmpty()) {
            PacketDistributor.sendToServer(new BCPayloads.CreateBountyRequest(candidates.get(index).getId()));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (confirm != null) {
            confirm.active = !candidates.isEmpty() && menu.getSlot(BountyCreateMenu.REWARD_SLOT).hasItem();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF555555);
        g.fill(leftPos + 30, topPos + 18, leftPos + 146, topPos + 36, 0xFF2A1010);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, slot.index == BountyCreateMenu.REWARD_SLOT ? 0xFF8B0000 : 0xFF8B8B8B);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
        Component target = candidates.isEmpty()
                ? Component.translatable("bloodcontracts.gui.no_target")
                : Component.literal(candidates.get(index).getName());
        g.drawCenteredString(font, target, 88, 23, 0xFFFF5555);
        g.drawString(font, Component.translatable("bloodcontracts.gui.reward"), 8, BountyCreateMenu.REWARD_Y + 4, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
