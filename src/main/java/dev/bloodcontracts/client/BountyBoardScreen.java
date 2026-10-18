package dev.bloodcontracts.client;

import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.PendingDelivery;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.DeliveryView;
import dev.bloodcontracts.network.BountyView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Bounty board, styled as a wall of wanted posters: open bounties, rewards waiting to be claimed, history.
 * Purely a view: every action is an intention sent to the server, which answers with a fresh list.
 */
public class BountyBoardScreen extends Screen {
    private enum Tab { OPEN, CLAIM, HISTORY }

    private static final int WIDTH = 330;
    private static final int HEIGHT = 236;
    private static final int ROW_HEIGHT = 30;
    private static final int ROWS = 4;
    private static final int LIST_TOP = 66;
    private static final int LIST_LEFT = 12;
    private static final int LIST_RIGHT = WIDTH - 20;
    private static final int MAX_ICONS = 4;

    private List<BountyView> active = List.of();
    private List<BountyView> history = List.of();
    private List<DeliveryView> claimable = List.of();
    private Tab tab = Tab.OPEN;
    private int scroll;
    private @Nullable UUID selected;
    private @Nullable Component status;
    private boolean statusIsError;
    private boolean loaded;

    private BCGui.TabButton openTab;
    private BCGui.TabButton claimTab;
    private BCGui.TabButton historyTab;
    private BCGui.BloodButton cancelButton;
    private BCGui.BloodButton newButton;
    private BCGui.BloodButton claimButton;
    private BCGui.BloodButton claimAllButton;

    public BountyBoardScreen() {
        super(Component.translatable("bloodcontracts.gui.board.title"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        openTab = addRenderableWidget(new BCGui.TabButton(l + LIST_LEFT, t + 46, 100, 16, Component.empty(), b -> switchTab(Tab.OPEN)));
        claimTab = addRenderableWidget(new BCGui.TabButton(l + LIST_LEFT + 103, t + 46, 100, 16, Component.empty(), b -> switchTab(Tab.CLAIM)));
        historyTab = addRenderableWidget(new BCGui.TabButton(l + LIST_LEFT + 206, t + 46, 100, 16, Component.empty(), b -> switchTab(Tab.HISTORY)));
        int by = t + HEIGHT - 30;
        cancelButton = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 100, 20,
                Component.translatable("bloodcontracts.gui.cancel"), b -> cancelSelected(), true));
        newButton = addRenderableWidget(new BCGui.BloodButton(l + 116, by, 100, 20, Component.translatable("bloodcontracts.gui.new"),
                b -> PacketDistributor.sendToServer(BCPayloads.OpenCreateMenuRequest.INSTANCE), true));
        claimButton = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 100, 20, Component.translatable("bloodcontracts.gui.claim"),
                b -> claim(Optional.ofNullable(selected)), true));
        claimAllButton = addRenderableWidget(new BCGui.BloodButton(l + 116, by, 100, 20, Component.translatable("bloodcontracts.gui.claim_all"),
                b -> claim(Optional.empty()), true));
        addRenderableWidget(new BCGui.BloodButton(l + WIDTH - 108, by, 96, 20, Component.translatable("gui.done"), b -> onClose(), false));
        refreshWidgets();
    }

    public void update(BountyListPayload payload) {
        active = payload.active();
        history = payload.history();
        claimable = payload.claimable();
        loaded = true;
        payload.status().ifPresent(s -> {
            status = s;
            statusIsError = payload.statusIsError();
        });
        if (selected != null && rowIds().stream().noneMatch(selected::equals)) {
            selected = null;
        }
        scroll = Math.max(0, Math.min(scroll, rowCount() - ROWS));
        refreshWidgets();
    }

    private int rowCount() {
        return switch (tab) {
            case OPEN -> active.size();
            case CLAIM -> claimable.size();
            case HISTORY -> history.size();
        };
    }

    private List<UUID> rowIds() {
        return switch (tab) {
            case OPEN -> active.stream().map(BountyView::id).toList();
            case CLAIM -> claimable.stream().map(DeliveryView::id).toList();
            case HISTORY -> history.stream().map(BountyView::id).toList();
        };
    }

    private void switchTab(Tab to) {
        tab = to;
        scroll = 0;
        selected = null;
        refreshWidgets();
    }

    private @Nullable BountyView selectedBounty() {
        return tab == Tab.OPEN ? active.stream().filter(v -> v.id().equals(selected)).findFirst().orElse(null) : null;
    }

    private void refreshWidgets() {
        if (openTab == null) {
            return;
        }
        openTab.setMessage(Component.translatable("bloodcontracts.gui.tab.active", active.size()));
        claimTab.setMessage(Component.translatable("bloodcontracts.gui.tab.claim", claimable.size()));
        historyTab.setMessage(Component.translatable("bloodcontracts.gui.tab.history", history.size()));
        openTab.selected = tab == Tab.OPEN;
        claimTab.selected = tab == Tab.CLAIM;
        historyTab.selected = tab == Tab.HISTORY;
        claimTab.attention = !claimable.isEmpty();

        cancelButton.visible = tab == Tab.OPEN;
        BountyView sel = selectedBounty();
        cancelButton.active = sel != null && sel.mine();
        newButton.visible = tab != Tab.CLAIM;
        claimButton.visible = claimAllButton.visible = tab == Tab.CLAIM;
        claimButton.active = tab == Tab.CLAIM && selected != null;
        claimAllButton.active = !claimable.isEmpty();
    }

    private void cancelSelected() {
        BountyView sel = selectedBounty();
        if (sel != null) {
            PacketDistributor.sendToServer(new BCPayloads.CancelBountyRequest(sel.id()));
        }
    }

    private void claim(Optional<UUID> delivery) {
        PacketDistributor.sendToServer(new BCPayloads.ClaimRewardRequest(delivery));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        BCGui.panel(g, l, t, WIDTH, HEIGHT);
        BCGui.drop(g, l + 14, t + 11);
        BCGui.drop(g, l + WIDTH - 30, t + 11);
        BCGui.title(g, font, title, l + WIDTH / 2, t + 12, 1.6f);
        long paid = history.stream().filter(v -> v.state() == BountyState.CLAIMED).count();
        BCGui.centered(g, font, Component.translatable("bloodcontracts.gui.board.subtitle", active.size(), paid),
                l + WIDTH / 2, t + 29, BCGui.INK_LIGHT);
        BCGui.divider(g, l + 14, l + WIDTH - 14, t + 41);
        g.fill(l + LIST_LEFT, t + 62, l + WIDTH - 12, t + 63, 0xFF3B2412);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();

        if (!loaded || rowCount() == 0) {
            Component empty = !loaded ? Component.translatable("bloodcontracts.gui.loading") : Component.translatable(switch (tab) {
                case OPEN -> "bloodcontracts.gui.board.empty";
                case CLAIM -> "bloodcontracts.gui.claim.empty";
                case HISTORY -> "bloodcontracts.command.no_history";
            });
            BCGui.drop(g, l + WIDTH / 2 - 8, t + LIST_TOP + 30);
            BCGui.centered(g, font, empty, l + WIDTH / 2, t + LIST_TOP + 52, BCGui.INK_LIGHT);
        }

        List<Component> tooltip = null;
        ItemStack itemTooltip = ItemStack.EMPTY;
        for (int i = 0; i < ROWS && scroll + i < rowCount(); i++) {
            int x = l + LIST_LEFT, y = t + LIST_TOP + i * ROW_HEIGHT, right = l + LIST_RIGHT;
            UUID id = rowIds().get(scroll + i);
            boolean over = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            BCGui.inset(g, x, y, right - x, ROW_HEIGHT - 2, id.equals(selected));
            if (over && !id.equals(selected)) {
                g.fill(x + 1, y + 1, right - 1, y + ROW_HEIGHT - 3, 0x18000000);
            }
            if (tab == Tab.CLAIM) {
                DeliveryView d = claimable.get(scroll + i);
                renderDelivery(g, d, x, y);
                if (over) {
                    itemTooltip = mouseX < x + 26 ? d.stack() : ItemStack.EMPTY;
                    tooltip = itemTooltip.isEmpty() ? List.of(Component.translatable("bloodcontracts.gui.hint.claim")) : null;
                }
            } else {
                BountyView v = (tab == Tab.OPEN ? active : history).get(scroll + i);
                ItemStack hoveredIcon = renderCard(g, v, x, y, right, over ? mouseX : -1, mouseY);
                if (over) {
                    itemTooltip = hoveredIcon;
                    tooltip = hoveredIcon.isEmpty() ? details(v) : null;
                }
            }
        }
        BCGui.scrollbar(g, l + WIDTH - 17, t + LIST_TOP, ROWS * ROW_HEIGHT - 2, rowCount(), ROWS, scroll);

        if (status != null) {
            BCGui.centered(g, font, status, l + WIDTH / 2, t + LIST_TOP + ROWS * ROW_HEIGHT + 4, statusIsError ? BCGui.BLOOD_BRIGHT : BCGui.SUCCESS);
        }
        if (!itemTooltip.isEmpty()) {
            g.renderTooltip(font, itemTooltip, mouseX, mouseY);
        } else if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** Draws a bounty card; returns the reward icon under the mouse, if any. */
    private ItemStack renderCard(GuiGraphics g, BountyView v, int x, int y, int right, int mouseX, int mouseY) {
        BCGui.face(g, v.targetId(), x + 5, y + 4, 20);
        int tx = x + 31;
        Component name = Component.literal(v.targetName()).withStyle(s -> s.withBold(true));
        g.drawString(font, name, tx, y + 5, BCGui.BLOOD, false);
        if (v.mine()) {
            g.drawString(font, Component.translatable("bloodcontracts.gui.mine"), tx + font.width(name) + 4, y + 5, BCGui.INK_LIGHT, false);
        }
        int icons = Math.min(MAX_ICONS, v.rewards().size());
        int iconsLeft = right - 7 - icons * 18;
        Component second = v.state() == BountyState.CLAIMED
                ? Component.translatable("bloodcontracts.command.claimed_by", v.hunterName().orElse("?"))
                : rewardText(v.rewards());
        String clipped = font.plainSubstrByWidth(second.getString(), iconsLeft - tx - 34);
        g.drawString(font, clipped, tx, y + 16, v.state() == BountyState.ACTIVE ? BCGui.AMBER : BCGui.INK_LIGHT, false);

        String date = new SimpleDateFormat("dd/MM HH:mm").format(new Date(v.state() == BountyState.ACTIVE ? v.createdAt() : v.closedAt()));
        g.drawString(font, date, iconsLeft - 4 - font.width(date), y + 5, BCGui.INK_LIGHT, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < icons; i++) {
            int ix = iconsLeft + i * 18;
            BCGui.slot(g, ix, y + 5, v.state() == BountyState.ACTIVE);
            ItemStack stack = v.rewards().get(i);
            g.renderItem(stack, ix + 1, y + 6);
            g.renderItemDecorations(font, stack, ix + 1, y + 6);
            if (mouseX >= ix && mouseX < ix + 18 && mouseY >= y + 5 && mouseY < y + 23) {
                hovered = stack;
            }
        }
        if (v.rewards().size() > MAX_ICONS) {
            String more = "+" + (v.rewards().size() - MAX_ICONS);
            g.drawString(font, more, iconsLeft - font.width(more) - 3, y + 16, BCGui.BLOOD, false);
        }

        if (v.state() == BountyState.CLAIMED) {
            BCGui.stamp(g, font, Component.translatable("bloodcontracts.gui.stamp.paid"), iconsLeft - 50, y + 17, BCGui.BLOOD);
        } else if (v.state() == BountyState.CANCELLED) {
            BCGui.stamp(g, font, Component.translatable("bloodcontracts.gui.stamp.cancelled"), iconsLeft - 50, y + 17, 0xFF505050);
        }
        return hovered;
    }

    private void renderDelivery(GuiGraphics g, DeliveryView d, int x, int y) {
        BCGui.slot(g, x + 5, y + 5, true);
        g.renderItem(d.stack(), x + 6, y + 6);
        g.renderItemDecorations(font, d.stack(), x + 6, y + 6);
        MutableComponent name = Component.literal(d.stack().getCount() + "× ").append(d.stack().getHoverName());
        g.drawString(font, name.withStyle(s -> s.withBold(true)), x + 30, y + 5, BCGui.AMBER, false);
        Component why = Component.translatable(d.reason() == PendingDelivery.Reason.REWARD
                ? "bloodcontracts.gui.claim.reward" : "bloodcontracts.gui.claim.refund", d.targetName());
        g.drawString(font, why, x + 30, y + 16, BCGui.INK_LIGHT, false);
    }

    private static Component rewardText(List<ItemStack> stacks) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(stacks.get(i).getCount() + "× ").append(stacks.get(i).getHoverName());
        }
        return Component.translatable("bloodcontracts.gui.reward_line", text);
    }

    private List<Component> details(BountyView v) {
        List<Component> lines = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        lines.add(Component.literal(v.targetName()).withStyle(s -> s.withColor(0xFF5555).withBold(true)));
        lines.add(Component.translatable("bloodcontracts.gui.rewards").withStyle(s -> s.withColor(0xAAAAAA)));
        for (ItemStack stack : v.rewards()) {
            lines.add(Component.literal("  " + stack.getCount() + "× ").append(stack.getHoverName()).withStyle(s -> s.withColor(0xFFAA00)));
        }
        lines.add(Component.translatable("bloodcontracts.gui.posted", fmt.format(new Date(v.createdAt()))).withStyle(s -> s.withColor(0xAAAAAA)));
        v.sponsorName().ifPresent(n -> lines.add(Component.translatable("bloodcontracts.command.info_sponsor", n).withStyle(s -> s.withColor(0xAAAAAA))));
        if (v.state() == BountyState.CLAIMED) {
            lines.add(Component.translatable("bloodcontracts.command.info_claimed", v.hunterName().orElse("?"), fmt.format(new Date(v.closedAt())))
                    .withStyle(s -> s.withColor(0xFFD700)));
        } else if (v.state() == BountyState.CANCELLED) {
            lines.add(Component.translatable("bloodcontracts.state.cancelled").withStyle(s -> s.withColor(0x888888)));
        } else if (v.mine()) {
            lines.add(Component.translatable("bloodcontracts.gui.hint.cancel").withStyle(s -> s.withColor(0x888888).withItalic(true)));
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int l = left(), t = top();
        for (int i = 0; i < ROWS && scroll + i < rowCount(); i++) {
            int y = t + LIST_TOP + i * ROW_HEIGHT;
            if (mouseX >= l + LIST_LEFT && mouseX < l + LIST_RIGHT && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                UUID clicked = rowIds().get(scroll + i);
                selected = clicked.equals(selected) ? null : clicked;
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, rowCount() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
