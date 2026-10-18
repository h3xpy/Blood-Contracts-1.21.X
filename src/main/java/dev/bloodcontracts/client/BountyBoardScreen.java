package dev.bloodcontracts.client;

import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BountyView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Bounty board: active bounties and history, details of the selected entry, cancel and "new bounty" actions.
 * Purely a view: every action is an intention sent to the server, which answers with a fresh list.
 */
public class BountyBoardScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 222;
    private static final int ROW_HEIGHT = 22;
    private static final int ROWS = 6;
    private static final int LIST_TOP = 42;

    private List<BountyView> active = List.of();
    private List<BountyView> history = List.of();
    private boolean showHistory;
    private int scroll;
    private @Nullable BountyView selected;
    private @Nullable Component status;
    private boolean statusIsError;
    private boolean loaded;

    private Button activeTab;
    private Button historyTab;
    private Button cancelButton;

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
        activeTab = addRenderableWidget(Button.builder(Component.empty(), b -> switchTab(false)).bounds(l + 8, t + 18, 100, 18).build());
        historyTab = addRenderableWidget(Button.builder(Component.empty(), b -> switchTab(true)).bounds(l + 112, t + 18, 100, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("bloodcontracts.gui.cancel"), b -> cancelSelected())
                .bounds(l + 8, t + HEIGHT - 26, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("bloodcontracts.gui.new"),
                b -> PacketDistributor.sendToServer(BCPayloads.OpenCreateMenuRequest.INSTANCE))
                .bounds(l + 112, t + HEIGHT - 26, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(l + WIDTH - 104, t + HEIGHT - 26, 96, 20).build());
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
        activeTab.active = showHistory;
        historyTab.active = !showHistory;
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
        g.fill(l, t, l + WIDTH, t + HEIGHT, 0xF0201010);
        g.renderOutline(l, t, WIDTH, HEIGHT, 0xFF8B0000);
        g.fill(l + 6, t + LIST_TOP - 2, l + WIDTH - 6, t + LIST_TOP + ROWS * ROW_HEIGHT + 2, 0xFF100808);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        g.drawCenteredString(font, title, l + WIDTH / 2, t + 6, 0xFFE04040);

        List<BountyView> rows = rows();
        if (!loaded) {
            g.drawCenteredString(font, Component.translatable("bloodcontracts.gui.loading"), l + WIDTH / 2, t + LIST_TOP + 30, 0xFFAAAAAA);
        } else if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(showHistory ? "bloodcontracts.command.no_history" : "bloodcontracts.command.none_active"),
                    l + WIDTH / 2, t + LIST_TOP + 30, 0xFFAAAAAA);
        }
        BountyView hovered = null;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            BountyView v = rows.get(scroll + i);
            int x = l + 8, y = t + LIST_TOP + i * ROW_HEIGHT;
            boolean isHovered = mouseX >= x && mouseX < l + WIDTH - 8 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (v == selected) {
                g.fill(x, y, l + WIDTH - 8, y + ROW_HEIGHT, 0xFF5A1A1A);
            } else if (isHovered) {
                g.fill(x, y, l + WIDTH - 8, y + ROW_HEIGHT, 0xFF301414);
            }
            g.renderItem(v.reward(), x + 2, y + 3);
            g.renderItemDecorations(font, v.reward(), x + 2, y + 3);
            Component line1 = Component.literal(v.targetName()).withStyle(ChatFormatting.RED)
                    .append(Component.literal("  " + v.reward().getCount() + "x ").withStyle(ChatFormatting.GOLD))
                    .append(v.reward().getHoverName().copy().withStyle(ChatFormatting.GOLD));
            g.drawString(font, line1, x + 22, y + 2, 0xFFFFFFFF);
            g.drawString(font, secondLine(v), x + 22, y + 12, 0xFF999999);
            if (isHovered && mouseX < x + 20) {
                hovered = v;
            }
        }
        if (rows.size() > ROWS) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(rows.size(), scroll + ROWS) + "/" + rows.size(),
                    l + WIDTH - 50, t + 24, 0xFF888888);
        }
        if (status != null) {
            g.drawCenteredString(font, status, l + WIDTH / 2, t + LIST_TOP + ROWS * ROW_HEIGHT + 8, statusIsError ? 0xFFFF5555 : 0xFF55FF55);
        }
        if (hovered != null) {
            g.renderTooltip(font, hovered.reward(), mouseX, mouseY);
        }
    }

    private Component secondLine(BountyView v) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(v.state() == BountyState.ACTIVE ? v.createdAt() : v.closedAt()));
        var line = Component.literal(date);
        if (v.state() == BountyState.CLAIMED) {
            line.append(" — ").append(Component.translatable("bloodcontracts.command.claimed_by", v.hunterName().orElse("?")));
        } else if (v.state() == BountyState.CANCELLED) {
            line.append(" — ").append(Component.translatable("bloodcontracts.state.cancelled"));
        }
        v.sponsorName().ifPresent(s -> line.append(Component.translatable("bloodcontracts.command.by", s)));
        if (v.mine()) {
            line.append(" ").append(Component.translatable("bloodcontracts.gui.mine"));
        }
        return line;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int l = left(), t = top();
        List<BountyView> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            int y = t + LIST_TOP + i * ROW_HEIGHT;
            if (mouseX >= l + 8 && mouseX < l + WIDTH - 8 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                selected = rows.get(scroll + i);
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
