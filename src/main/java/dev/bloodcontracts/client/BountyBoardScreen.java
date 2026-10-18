package dev.bloodcontracts.client;

import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BountyView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Bounty board, styled as a wall of wanted posters: active bounties and history, details on hover, cancel and
 * "new bounty" actions. Purely a view: every action is an intention sent to the server, which answers with a fresh list.
 */
public class BountyBoardScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 236;
    private static final int ROW_HEIGHT = 30;
    private static final int ROWS = 4;
    private static final int LIST_TOP = 66;
    private static final int LIST_LEFT = 12;
    private static final int LIST_RIGHT = WIDTH - 20;

    private List<BountyView> active = List.of();
    private List<BountyView> history = List.of();
    private boolean showHistory;
    private int scroll;
    private @Nullable BountyView selected;
    private @Nullable Component status;
    private boolean statusIsError;
    private boolean loaded;

    private BCGui.TabButton activeTab;
    private BCGui.TabButton historyTab;
    private BCGui.BloodButton cancelButton;

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
        activeTab = addRenderableWidget(new BCGui.TabButton(l + LIST_LEFT, t + 46, 110, 16, Component.empty(), b -> switchTab(false)));
        historyTab = addRenderableWidget(new BCGui.TabButton(l + LIST_LEFT + 114, t + 46, 110, 16, Component.empty(), b -> switchTab(true)));
        int by = t + HEIGHT - 30;
        cancelButton = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 100, 20,
                Component.translatable("bloodcontracts.gui.cancel"), b -> cancelSelected(), true));
        addRenderableWidget(new BCGui.BloodButton(l + 116, by, 100, 20, Component.translatable("bloodcontracts.gui.new"),
                b -> PacketDistributor.sendToServer(BCPayloads.OpenCreateMenuRequest.INSTANCE), true));
        addRenderableWidget(new BCGui.BloodButton(l + WIDTH - 108, by, 96, 20, Component.translatable("gui.done"), b -> onClose(), false));
        refreshWidgets();
    }

    public void update(BountyListPayload payload) {
        active = payload.active();
        history = payload.history();
        loaded = true;
        payload.status().ifPresent(s -> {
            status = s;
            statusIsError = payload.statusIsError();
        });
        if (selected != null) {
            BountyView previous = selected;
            selected = rows().stream().filter(v -> v.id().equals(previous.id())).findFirst().orElse(null);
        }
        scroll = Math.max(0, Math.min(scroll, rows().size() - ROWS));
        refreshWidgets();
    }

    private List<BountyView> rows() {
        return showHistory ? history : active;
    }

    private void switchTab(boolean toHistory) {
        showHistory = toHistory;
        scroll = 0;
        selected = null;
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (activeTab == null) {
            return;
        }
        activeTab.setMessage(Component.translatable("bloodcontracts.gui.tab.active", active.size()));
        historyTab.setMessage(Component.translatable("bloodcontracts.gui.tab.history", history.size()));
        activeTab.selected = !showHistory;
        historyTab.selected = showHistory;
        cancelButton.visible = !showHistory;
        cancelButton.active = selected != null && selected.mine() && selected.state() == BountyState.ACTIVE;
    }

    private void cancelSelected() {
        if (selected != null) {
            PacketDistributor.sendToServer(new BCPayloads.CancelBountyRequest(selected.id()));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        BCGui.panel(g, l, t, WIDTH, HEIGHT);
        BCGui.drop(g, l + 14, t + 11);
        BCGui.drop(g, l + WIDTH - 30, t + 11);
        BCGui.title(g, font, title, l + WIDTH / 2, t + 12, 1.6f);
        long claimed = history.stream().filter(v -> v.state() == BountyState.CLAIMED).count();
        BCGui.centered(g, font, Component.translatable("bloodcontracts.gui.board.subtitle", active.size(), claimed),
                l + WIDTH / 2, t + 29, BCGui.INK_LIGHT);
        BCGui.divider(g, l + 14, l + WIDTH - 14, t + 41);
        // page under the tabs
        g.fill(l + LIST_LEFT, t + 62, l + WIDTH - 12, t + 63, 0xFF3B2412);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();

        List<BountyView> rows = rows();
        if (!loaded || rows.isEmpty()) {
            Component empty = !loaded ? Component.translatable("bloodcontracts.gui.loading")
                    : Component.translatable(showHistory ? "bloodcontracts.command.no_history" : "bloodcontracts.gui.board.empty");
            BCGui.drop(g, l + WIDTH / 2 - 8, t + LIST_TOP + 30);
            BCGui.centered(g, font, empty, l + WIDTH / 2, t + LIST_TOP + 52, BCGui.INK_LIGHT);
        }

        BountyView hovered = null;
        boolean hoveredItem = false;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            BountyView v = rows.get(scroll + i);
            int x = l + LIST_LEFT, y = t + LIST_TOP + i * ROW_HEIGHT;
            int right = l + LIST_RIGHT;
            boolean over = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            BCGui.inset(g, x, y, right - x, ROW_HEIGHT - 2, v == selected);
            if (over && v != selected) {
                g.fill(x + 1, y + 1, right - 1, y + ROW_HEIGHT - 3, 0x18000000);
            }
            renderCard(g, v, x, y, right);
            if (over) {
                hovered = v;
                hoveredItem = mouseX >= right - 25 && mouseX < right - 7;
            }
        }
        BCGui.scrollbar(g, l + WIDTH - 17, t + LIST_TOP, ROWS * ROW_HEIGHT - 2, rows.size(), ROWS, scroll);

        if (status != null) {
            BCGui.centered(g, font, status, l + WIDTH / 2, t + LIST_TOP + ROWS * ROW_HEIGHT + 4, statusIsError ? BCGui.BLOOD_BRIGHT : BCGui.SUCCESS);
        }
        if (hovered != null) {
            if (hoveredItem) {
                g.renderTooltip(font, hovered.reward(), mouseX, mouseY);
            } else {
                g.renderComponentTooltip(font, details(hovered), mouseX, mouseY);
            }
        }
    }

    private void renderCard(GuiGraphics g, BountyView v, int x, int y, int right) {
        BCGui.face(g, v.targetId(), x + 5, y + 4, 20);
        int tx = x + 31;
        g.drawString(font, Component.literal(v.targetName()).withStyle(s -> s.withBold(true)), tx, y + 5, BCGui.BLOOD, false);
        if (v.mine()) {
            int nameEnd = tx + font.width(Component.literal(v.targetName()).withStyle(s -> s.withBold(true))) + 4;
            g.drawString(font, Component.translatable("bloodcontracts.gui.mine"), nameEnd, y + 5, BCGui.INK_LIGHT, false);
        }
        MutableComponent reward = Component.literal(v.reward().getCount() + "× ").append(v.reward().getHoverName());
        Component second = v.state() == BountyState.CLAIMED
                ? Component.translatable("bloodcontracts.command.claimed_by", v.hunterName().orElse("?"))
                : Component.translatable("bloodcontracts.gui.reward_line", reward);
        g.drawString(font, second, tx, y + 16, v.state() == BountyState.ACTIVE ? BCGui.AMBER : BCGui.INK_LIGHT, false);

        String date = new SimpleDateFormat("dd/MM HH:mm").format(new Date(v.state() == BountyState.ACTIVE ? v.createdAt() : v.closedAt()));
        g.drawString(font, date, right - 30 - font.width(date), y + 5, BCGui.INK_LIGHT, false);

        BCGui.slot(g, right - 25, y + 5, v.state() == BountyState.ACTIVE);
        g.renderItem(v.reward(), right - 24, y + 6);
        g.renderItemDecorations(font, v.reward(), right - 24, y + 6);

        if (v.state() == BountyState.CLAIMED) {
            BCGui.stamp(g, font, Component.translatable("bloodcontracts.gui.stamp.paid"), right - 82, y + 17, BCGui.BLOOD);
        } else if (v.state() == BountyState.CANCELLED) {
            BCGui.stamp(g, font, Component.translatable("bloodcontracts.gui.stamp.cancelled"), right - 82, y + 17, 0xFF505050);
        }
    }

    private List<Component> details(BountyView v) {
        List<Component> lines = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        lines.add(Component.literal(v.targetName()).withStyle(s -> s.withColor(0xFF5555).withBold(true)));
        lines.add(Component.translatable("bloodcontracts.command.info_reward",
                Component.literal(v.reward().getCount() + "× ").append(v.reward().getHoverName())).withStyle(s -> s.withColor(0xFFAA00)));
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
        List<BountyView> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            int y = t + LIST_TOP + i * ROW_HEIGHT;
            if (mouseX >= l + LIST_LEFT && mouseX < l + LIST_RIGHT && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                BountyView clicked = rows.get(scroll + i);
                selected = clicked == selected ? null : clicked;
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, rows().size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
