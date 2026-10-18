package dev.bloodcontracts.client;

import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Bounty creation, styled as a blank wanted poster: pick any player the server knows (online or not, with a search box),
 * put the reward in the server-side slot (or up to 9 slots with "+ items"), sign. Only the target's UUID is sent; the
 * server takes the rewards from its own copy of the slots.
 */
public class BountyCreateScreen extends AbstractContainerScreen<BountyCreateMenu> {
    private static final int FACE = 32;
    private static final int LABEL_Y = BountyCreateMenu.REWARD_Y - 13;

    private List<KnownPlayer> candidates = List.of();
    private @Nullable UUID chosen;
    private int index;
    private EditBox search;
    private BCGui.BloodButton confirm;
    private BCGui.BloodButton previous;
    private BCGui.BloodButton next;
    private BCGui.BloodButton toggleSlots;

    public BountyCreateScreen(BountyCreateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = BountyCreateMenu.IMAGE_HEIGHT;
        inventoryLabelY = BountyCreateMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int faceX = leftPos + (imageWidth - FACE) / 2;
        previous = addRenderableWidget(new BCGui.BloodButton(faceX - 30, topPos + 26, 20, 20, Component.literal("◀"), b -> cycle(-1), false));
        next = addRenderableWidget(new BCGui.BloodButton(faceX + FACE + 10, topPos + 26, 20, 20, Component.literal("▶"), b -> cycle(1), false));
        search = new EditBox(font, leftPos + 9, topPos + 77, imageWidth - 18, 12, Component.translatable("bloodcontracts.gui.search"));
        search.setHint(Component.translatable("bloodcontracts.gui.search"));
        search.setMaxLength(16);
        search.setResponder(text -> refreshCandidates());
        addRenderableWidget(search);
        toggleSlots = addRenderableWidget(new BCGui.BloodButton(leftPos + imageWidth - 66, topPos + LABEL_Y - 2, 58, 12,
                Component.empty(), b -> toggleSlots(), false));
        confirm = addRenderableWidget(new BCGui.BloodButton(leftPos + (imageWidth - 110) / 2, topPos + BountyCreateMenu.REWARD_Y + 22, 110, 18,
                Component.translatable("bloodcontracts.gui.confirm"), b -> submit(), true));
        refreshCandidates();
    }

    /** All players the server knows (online first), filtered by the search box; falls back to the tab list. */
    void refreshCandidates() {
        if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }
        UUID self = minecraft.player.getUUID();
        List<KnownPlayer> all = ClientPayloadHandler.knownPlayers;
        if (all.isEmpty()) {
            all = minecraft.getConnection().getOnlinePlayers().stream()
                    .map(PlayerInfo::getProfile)
                    .map(p -> new KnownPlayer(p.getId(), p.getName(), true))
                    .toList();
        }
        String filter = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        candidates = all.stream()
                .filter(p -> !p.id().equals(self))
                .filter(p -> filter.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing((KnownPlayer p) -> !isOnline(p)).thenComparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int keep = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).id().equals(chosen)) {
                keep = i;
            }
        }
        index = keep >= 0 ? keep : 0;
        chosen = candidates.isEmpty() ? null : candidates.get(index).id();
    }

    private boolean isOnline(KnownPlayer p) {
        return minecraft != null && minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(p.id()) != null;
    }

    private void cycle(int delta) {
        if (!candidates.isEmpty()) {
            index = Math.floorMod(index + delta, candidates.size());
            chosen = candidates.get(index).id();
        }
    }

    private void toggleSlots() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BountyCreateMenu.BUTTON_TOGGLE_SLOTS);
        }
    }

    private void submit() {
        if (chosen != null) {
            PacketDistributor.sendToServer(new BCPayloads.CreateBountyRequest(chosen));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (confirm != null) {
            confirm.active = chosen != null && menu.hasAnyReward();
            previous.active = next.active = candidates.size() > 1;
            toggleSlots.setMessage(Component.translatable(menu.isExpanded() ? "bloodcontracts.gui.fewer_slots" : "bloodcontracts.gui.more_slots"));
            toggleSlots.active = !menu.isExpanded() || menu.canCollapse();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Typing in the search box must not close the screen with the inventory key.
        if (search != null && search.isFocused() && keyCode != 256) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        BCGui.panel(g, x, y, imageWidth, imageHeight);

        int faceX = x + (imageWidth - FACE) / 2, faceY = y + 20;
        KnownPlayer target = candidates.isEmpty() ? null : candidates.get(index);
        g.fill(faceX - 3, faceY - 3, faceX + FACE + 3, faceY + FACE + 3, target != null && !isOnline(target) ? 0xFF5A4A3A : BCGui.BLOOD);
        if (target == null) {
            g.fill(faceX - 1, faceY - 1, faceX + FACE + 1, faceY + FACE + 1, 0xFF2A180B);
            BCGui.centered(g, font, Component.literal("?"), faceX + FACE / 2, faceY + 12, 0xFFE8D8B4);
        } else {
            BCGui.face(g, target.id(), faceX, faceY, FACE);
            if (!isOnline(target)) {
                g.fill(faceX, faceY, faceX + FACE, faceY + FACE, 0x70404040);
            }
        }
        BCGui.rule(g, x + 10, x + imageWidth - 10, y + LABEL_Y - 4);

        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                BCGui.slot(g, x + slot.x - 1, y + slot.y - 1, slot.index < BountyCreateMenu.REWARD_SLOTS);
            }
        }
        if (!menu.getSlot(0).hasItem()) {
            int sx = x + BountyCreateMenu.REWARD_X, sy = y + BountyCreateMenu.REWARD_Y;
            g.fill(sx + 4, sy + 7, sx + 12, sy + 9, 0x80E8D8B4);
            g.fill(sx + 7, sy + 4, sx + 9, sy + 12, 0x80E8D8B4);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        BCGui.title(g, font, Component.translatable("bloodcontracts.gui.create.poster"), imageWidth / 2, 7, 1.3f);
        KnownPlayer target = candidates.isEmpty() ? null : candidates.get(index);
        Component name = target == null
                ? Component.translatable(ClientPayloadHandler.knownPlayers.isEmpty() ? "bloodcontracts.gui.no_target" : "bloodcontracts.gui.no_match")
                : Component.literal(target.name()).withStyle(s -> s.withBold(true));
        BCGui.centered(g, font, name, imageWidth / 2, 56, target == null ? BCGui.INK_LIGHT : BCGui.BLOOD);
        if (target != null) {
            boolean online = isOnline(target);
            BCGui.centered(g, font, Component.translatable(online ? "bloodcontracts.gui.online" : "bloodcontracts.gui.offline"),
                    imageWidth / 2, 66, online ? BCGui.SUCCESS : BCGui.INK_LIGHT);
        }
        if (candidates.size() > 1) {
            String counter = (index + 1) + "/" + candidates.size();
            g.drawString(font, counter, imageWidth - 10 - font.width(counter), 66, BCGui.INK_LIGHT, false);
        }
        g.drawString(font, Component.translatable("bloodcontracts.gui.reward"), 8, LABEL_Y, BCGui.INK, false);
        if (!menu.isExpanded()) {
            ItemStack reward = menu.getSlot(0).getItem();
            Component text = reward.isEmpty()
                    ? Component.translatable("bloodcontracts.gui.reward_hint")
                    : Component.literal(reward.getCount() + "× ").append(reward.getHoverName());
            g.drawString(font, font.plainSubstrByWidth(text.getString(), imageWidth - 40), BountyCreateMenu.REWARD_X + 22,
                    BountyCreateMenu.REWARD_Y + 5, reward.isEmpty() ? BCGui.INK_LIGHT : BCGui.AMBER, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BCGui.INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
