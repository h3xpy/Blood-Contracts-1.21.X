package dev.bloodcontracts.client;

import com.mojang.authlib.GameProfile;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Bounty creation, styled as a blank wanted poster: pick a target among online players, put the reward in the
 * (server-side) slot, sign. Only the target's UUID is sent; the server takes the reward from its own copy of the slot.
 */
public class BountyCreateScreen extends AbstractContainerScreen<BountyCreateMenu> {
    private static final int FACE = 32;

    private List<GameProfile> candidates = List.of();
    private int index;
    private BCGui.BloodButton confirm;
    private BCGui.BloodButton previous;
    private BCGui.BloodButton next;

    public BountyCreateScreen(BountyCreateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 196;
        inventoryLabelY = BountyCreateMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        refreshCandidates();
        int faceX = leftPos + (imageWidth - FACE) / 2;
        previous = addRenderableWidget(new BCGui.BloodButton(faceX - 30, topPos + 30, 20, 20, Component.literal("◀"), b -> cycle(-1), false));
        next = addRenderableWidget(new BCGui.BloodButton(faceX + FACE + 10, topPos + 30, 20, 20, Component.literal("▶"), b -> cycle(1), false));
        confirm = addRenderableWidget(new BCGui.BloodButton(leftPos + 98, topPos + BountyCreateMenu.REWARD_Y - 1, 68, 20,
                Component.translatable("bloodcontracts.gui.confirm"), b -> submit(), true));
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
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
            refreshCandidates();
        }
        if (confirm != null) {
            confirm.active = !candidates.isEmpty() && menu.getSlot(BountyCreateMenu.REWARD_SLOT).hasItem();
            previous.active = next.active = candidates.size() > 1;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        BCGui.panel(g, x, y, imageWidth, imageHeight);

        // Target: face in a frame, name below.
        int faceX = x + (imageWidth - FACE) / 2, faceY = y + 24;
        g.fill(faceX - 3, faceY - 3, faceX + FACE + 3, faceY + FACE + 3, BCGui.BLOOD);
        if (candidates.isEmpty()) {
            g.fill(faceX - 1, faceY - 1, faceX + FACE + 1, faceY + FACE + 1, 0xFF2A180B);
            BCGui.centered(g, font, Component.literal("?"), faceX + FACE / 2, faceY + 12, 0xFFE8D8B4);
        } else {
            BCGui.face(g, candidates.get(index).getId(), faceX, faceY, FACE);
        }
        BCGui.rule(g, x + 10, x + imageWidth - 10, y + BountyCreateMenu.REWARD_Y - 8);

        // Reward slot with a red frame, inventory slots.
        for (Slot slot : menu.slots) {
            boolean reward = slot.index == BountyCreateMenu.REWARD_SLOT;
            BCGui.slot(g, x + slot.x - 1, y + slot.y - 1, reward);
        }
        if (!menu.getSlot(BountyCreateMenu.REWARD_SLOT).hasItem()) {
            // ghost hint inside the empty reward slot
            g.fill(x + BountyCreateMenu.REWARD_X + 4, y + BountyCreateMenu.REWARD_Y + 7,
                    x + BountyCreateMenu.REWARD_X + 12, y + BountyCreateMenu.REWARD_Y + 9, 0x80E8D8B4);
            g.fill(x + BountyCreateMenu.REWARD_X + 7, y + BountyCreateMenu.REWARD_Y + 4,
                    x + BountyCreateMenu.REWARD_X + 9, y + BountyCreateMenu.REWARD_Y + 12, 0x80E8D8B4);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        BCGui.title(g, font, Component.translatable("bloodcontracts.gui.create.poster"), imageWidth / 2, 9, 1.3f);
        Component target = candidates.isEmpty()
                ? Component.translatable("bloodcontracts.gui.no_target")
                : Component.literal(candidates.get(index).getName()).withStyle(s -> s.withBold(true));
        BCGui.centered(g, font, target, imageWidth / 2, 62, candidates.isEmpty() ? BCGui.INK_LIGHT : BCGui.BLOOD);
        if (candidates.size() > 1) {
            String counter = (index + 1) + "/" + candidates.size();
            g.drawString(font, counter, imageWidth - 10 - font.width(counter), 62, BCGui.INK_LIGHT, false);
        }
        int textX = BountyCreateMenu.REWARD_X + 22;
        int textWidth = 98 - textX - 3;
        g.drawString(font, Component.translatable("bloodcontracts.gui.reward"), textX, BountyCreateMenu.REWARD_Y - 1, BCGui.INK, false);
        ItemStack reward = menu.getSlot(BountyCreateMenu.REWARD_SLOT).getItem();
        Component rewardText = reward.isEmpty()
                ? Component.translatable("bloodcontracts.gui.reward_hint")
                : Component.literal(reward.getCount() + "× ").append(reward.getHoverName());
        g.drawString(font, font.plainSubstrByWidth(rewardText.getString(), textWidth), textX, BountyCreateMenu.REWARD_Y + 9,
                reward.isEmpty() ? BCGui.INK_LIGHT : BCGui.AMBER, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BCGui.INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
