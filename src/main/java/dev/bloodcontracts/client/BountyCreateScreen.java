package dev.bloodcontracts.client;

import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import dev.bloodcontracts.network.TargetView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Bounty creation, styled as a blank wanted poster: pick any player the server knows (online or not, with a search box),
 * put the reward in the server-side slot (or up to 9 slots with "+ items"), choose public or private (with its hunters)
 * in the side panel, sign. Only UUIDs and the type are sent; the server takes the rewards from its own copy of the slots.
 */
public class BountyCreateScreen extends AbstractContainerScreen<BountyCreateMenu> {
    private static final int FACE = 32;
    private static final int LABEL_Y = BountyCreateMenu.REWARD_Y - 13;
    private static final int SIDE_W = 132;
    private static final int SIDE_GAP = 4;

    private List<KnownPlayer> candidates = List.of();
    private @Nullable UUID chosen;
    private int index;
    private BountyType type = BountyType.PUBLIC;
    private EditBox search;
    private EditBox hunterSearch;
    private HunterPicker picker;
    private BCGui.BloodButton confirm;
    private BCGui.BloodButton previous;
    private BCGui.BloodButton next;
    private BCGui.BloodButton toggleSlots;
    private BCGui.TabButton publicButton;
    private BCGui.TabButton privateButton;

    public BountyCreateScreen(BountyCreateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = BountyCreateMenu.IMAGE_HEIGHT;
        inventoryLabelY = BountyCreateMenu.INVENTORY_Y - 11;
    }

    private int sideX() {
        return leftPos + imageWidth + SIDE_GAP;
    }

    @Override
    protected void init() {
        super.init();
        // Center the poster and its side panel together.
        leftPos = (width - imageWidth - SIDE_GAP - SIDE_W) / 2;
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

        int sx = sideX();
        publicButton = addRenderableWidget(new BCGui.TabButton(sx + 8, topPos + 22, 56, 16,
                Component.translatable("bloodcontracts.type.public"), b -> type = BountyType.PUBLIC));
        privateButton = addRenderableWidget(new BCGui.TabButton(sx + 68, topPos + 22, 56, 16,
                Component.translatable("bloodcontracts.type.private"), b -> type = BountyType.PRIVATE));
        if (picker == null) {
            picker = new HunterPicker(this::excludedHunters);
        }
        hunterSearch = new EditBox(font, sx + 8, topPos + 58, SIDE_W - 16, 12, Component.translatable("bloodcontracts.gui.search"));
        hunterSearch.setHint(Component.translatable("bloodcontracts.gui.search"));
        hunterSearch.setMaxLength(16);
        hunterSearch.setResponder(picker::setFilter);
        addRenderableWidget(hunterSearch);
        addRenderableWidget(new BCGui.BloodButton(sx + 8, topPos + imageHeight - 24, SIDE_W - 16, 16,
                Component.translatable("bloodcontracts.gui.back_to_board"), b -> backToBoard(), false));
        refreshCandidates();
    }

    private List<UUID> excludedHunters() {
        List<UUID> out = new ArrayList<>();
        if (minecraft != null && minecraft.player != null) {
            out.add(minecraft.player.getUUID());
        }
        if (chosen != null) {
            out.add(chosen);
        }
        return out;
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
            List<UUID> hunters = type == BountyType.PRIVATE ? List.copyOf(picker.selected) : List.of();
            PacketDistributor.sendToServer(new BCPayloads.CreateBountyRequest(chosen, type, hunters));
        }
    }

    /** Closing the menu gives the items back (server side); then the board opens again. */
    private void backToBoard() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            minecraft.setScreen(new BountyBoardScreen());
            PacketDistributor.sendToServer(BCPayloads.RequestBountyList.INSTANCE);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (confirm != null) {
            boolean huntersOk = type == BountyType.PUBLIC || !picker.selected.isEmpty();
            confirm.active = chosen != null && menu.hasAnyReward() && huntersOk;
            previous.active = next.active = candidates.size() > 1;
            toggleSlots.setMessage(Component.translatable(menu.isExpanded() ? "bloodcontracts.gui.fewer_slots" : "bloodcontracts.gui.more_slots"));
            toggleSlots.active = !menu.isExpanded() || menu.canCollapse();
            publicButton.selected = type == BountyType.PUBLIC;
            privateButton.selected = type == BountyType.PRIVATE;
            hunterSearch.visible = type == BountyType.PRIVATE;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Typing in a search box must not close the screen with the inventory key.
        for (EditBox box : new EditBox[] {search, hunterSearch}) {
            if (box != null && box.isFocused() && box.visible && keyCode != 256) {
                return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (type == BountyType.PRIVATE && picker.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (type == BountyType.PRIVATE && picker.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Clicks on the side panel are not "outside": they must never drop the carried item. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean inSide = mouseX >= sideX() && mouseX < sideX() + SIDE_W && mouseY >= topPos && mouseY < topPos + imageHeight;
        return !inSide && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        BCGui.panel(g, x, y, imageWidth, imageHeight);
        BCGui.panel(g, sideX(), y, SIDE_W, imageHeight);

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
        renderSide(g, mouseX, mouseY);
    }

    /** Type of bounty, the public warning or the hunter list, and recurring pairs on the chosen target. */
    private void renderSide(GuiGraphics g, int mouseX, int mouseY) {
        int sx = sideX(), y = topPos;
        BCGui.centered(g, font, Component.translatable("bloodcontracts.gui.create.type"), sx + SIDE_W / 2, y + 10, BCGui.BLOOD);
        int textY = y + 44;
        if (type == BountyType.PUBLIC) {
            for (FormattedCharSequence line : font.split(Component.translatable("bloodcontracts.gui.public_warning"), SIDE_W - 18)) {
                g.drawString(font, line, sx + 9, textY, BCGui.BLOOD, false);
                textY += 10;
            }
            textY += 4;
            for (FormattedCharSequence line : font.split(Component.translatable("bloodcontracts.gui.public_hint"), SIDE_W - 18)) {
                g.drawString(font, line, sx + 9, textY, BCGui.INK_LIGHT, false);
                textY += 10;
            }
        } else {
            g.drawString(font, Component.translatable("bloodcontracts.gui.hunters_selected", picker.selected.size(),
                    ClientPayloadHandler.helpConfig.maxHunterListSize()), sx + 9, y + 46, BCGui.INK, false);
            picker.render(g, font, sx + 8, y + 74, SIDE_W - 16, 104, mouseX, mouseY, 0);
            textY = y + 182;
        }
        // Information only: hunters who keep collecting bounties on this target.
        if (chosen != null) {
            String targetName = candidates.isEmpty() ? "?" : candidates.get(index).name();
            for (TargetView.RepeatView r : ClientPayloadHandler.repeatPairs) {
                if (!r.target().equals(chosen)) {
                    continue;
                }
                for (FormattedCharSequence line : font.split(Component.translatable("bloodcontracts.gui.repeat_pair", r.hunterName(), r.count(), targetName), SIDE_W - 18)) {
                    if (textY < y + imageHeight - 34) {
                        g.drawString(font, line, sx + 9, textY, BCGui.INK_LIGHT, false);
                    }
                    textY += 10;
                }
            }
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
