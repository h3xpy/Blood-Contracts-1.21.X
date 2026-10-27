package dev.bloodcontracts.client;

import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.core.PendingDelivery;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.DeliveryView;
import dev.bloodcontracts.network.BountyView;
import dev.bloodcontracts.network.TargetView;
import dev.bloodcontracts.util.Durations;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The bounty board: a wall of wanted posters and the player's own contracts. Tabs: wanted, create, my bounties, to claim,
 * history, how it works. Purely a view: every action is an intention sent to the server, which answers with a fresh list.
 */
public class BountyBoardScreen extends Screen {
    private enum Tab { WANTED, CREATE, MINE, CLAIM, HISTORY, HELP }

    private static final int WIDTH = 392;
    private static final int HEIGHT = 250;
    private static final int TOP = 30;
    private static final int BOTTOM = 222;
    private static final int COLS = 5;
    private static final int GRID_ROWS = 2;
    private static final int GAP_X = 6;
    private static final int GAP_Y = 4;
    private static final int ROW_HEIGHT = 30;
    private static final int LIST_ROWS = 6;

    private List<TargetView> wanted = List.of();
    private List<BountyView> history = List.of();
    private List<DeliveryView> claimable = List.of();
    private long clockOffset;
    private Tab tab = Tab.WANTED;
    private int page;
    private int scroll;
    private @Nullable UUID selectedDelivery;
    private @Nullable UUID detail;
    private @Nullable Component status;
    private boolean statusIsError;
    private boolean loaded;
    private final Map<UUID, Float> hover = new HashMap<>();

    private final Map<Tab, BCGui.TabButton> tabs = new HashMap<>();
    private BCGui.BloodButton prev;
    private BCGui.BloodButton next;
    private BCGui.BloodButton back;
    private BCGui.BloodButton newBounty;
    private BCGui.BloodButton claimButton;
    private BCGui.BloodButton claimAllButton;
    private BCGui.BloodButton addHunters;
    private BCGui.BloodButton makePublic;
    private BCGui.BloodButton cancel;
    private BCGui.BloodButton resume;
    // hunter picker (modal over the detail view)
    private boolean picking;
    private HunterPicker picker;
    private EditBox pickerSearch;
    private BCGui.BloodButton pickerConfirm;
    private BCGui.BloodButton pickerClose;

    public BountyBoardScreen() {
        super(Component.translatable("bloodcontracts.gui.board.title"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private long now() {
        return System.currentTimeMillis() + clockOffset;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        int tabW = (WIDTH - 20 - 3 * (Tab.values().length - 1)) / Tab.values().length;
        int x = l + 10;
        for (Tab tb : Tab.values()) {
            BCGui.TabButton button = addRenderableWidget(new BCGui.TabButton(x, t + 9, tabW, 16, tabLabel(tb), b -> switchTab(tb)));
            tabs.put(tb, button);
            x += tabW + 3;
        }
        int by = t + HEIGHT - 25;
        prev = addRenderableWidget(new BCGui.BloodButton(l + WIDTH / 2 - 46, by, 20, 16, Component.literal("◀"), b -> turn(-1), false));
        next = addRenderableWidget(new BCGui.BloodButton(l + WIDTH / 2 + 26, by, 20, 16, Component.literal("▶"), b -> turn(1), false));
        back = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 70, 16, Component.translatable("bloodcontracts.gui.back"), b -> {
            detail = null;
            refreshWidgets();
        }, false));
        newBounty = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 100, 16, Component.translatable("bloodcontracts.gui.new"),
                b -> openCreate(), true));
        claimButton = addRenderableWidget(new BCGui.BloodButton(l + 12, by, 90, 16, Component.translatable("bloodcontracts.gui.claim"),
                b -> claim(Optional.ofNullable(selectedDelivery)), true));
        claimAllButton = addRenderableWidget(new BCGui.BloodButton(l + 106, by, 90, 16, Component.translatable("bloodcontracts.gui.claim_all"),
                b -> claim(Optional.empty()), true));
        addRenderableWidget(new BCGui.BloodButton(l + WIDTH - 72, by, 60, 16, Component.translatable("gui.done"), b -> onClose(), false));

        int ay = t + BOTTOM - 20, ax = l + 132;
        addHunters = addRenderableWidget(new BCGui.BloodButton(ax, ay, 78, 16, Component.translatable("bloodcontracts.gui.add_hunters"),
                b -> openPicker(), false));
        makePublic = addRenderableWidget(new BCGui.BloodButton(ax + 81, ay, 78, 16, Component.translatable("bloodcontracts.gui.make_public"),
                b -> myBounty().ifPresent(v -> PacketDistributor.sendToServer(new BCPayloads.MakePublicRequest(v.id()))), false));
        cancel = addRenderableWidget(new BCGui.BloodButton(ax + 162, ay, 84, 16, Component.translatable("bloodcontracts.gui.cancel"),
                b -> myBounty().ifPresent(v -> PacketDistributor.sendToServer(new BCPayloads.CancelBountyRequest(v.id()))), true));
        resume = addRenderableWidget(new BCGui.BloodButton(ax + 162, ay, 84, 16, Component.translatable("bloodcontracts.gui.resume"),
                b -> myBounty().ifPresent(v -> PacketDistributor.sendToServer(new BCPayloads.ResumeBountyRequest(v.id()))), true));

        picker = new HunterPicker(this::pickerExcluded);
        int px = l + (WIDTH - 200) / 2, py = t + 30;
        // The picker's widgets are drawn and fed by hand, above the dimmed board.
        pickerSearch = new EditBox(font, px + 8, py + 20, 184, 12, Component.translatable("bloodcontracts.gui.search"));
        pickerSearch.setHint(Component.translatable("bloodcontracts.gui.search"));
        pickerSearch.setResponder(picker::setFilter);
        pickerConfirm = new BCGui.BloodButton(px + 8, py + 166, 90, 16, Component.translatable("bloodcontracts.gui.add"),
                b -> confirmPicker(), true);
        pickerClose = new BCGui.BloodButton(px + 102, py + 166, 90, 16, Component.translatable("gui.cancel"), b -> {
            picking = false;
            refreshWidgets();
        }, false);
        refreshWidgets();
    }

    private Component tabLabel(Tab tb) {
        return switch (tb) {
            case WANTED -> Component.translatable("bloodcontracts.gui.tab.wanted");
            case CREATE -> Component.translatable("bloodcontracts.gui.tab.create");
            case MINE -> Component.translatable("bloodcontracts.gui.tab.mine");
            case CLAIM -> Component.translatable("bloodcontracts.gui.tab.claim");
            case HISTORY -> Component.translatable("bloodcontracts.gui.tab.history");
            case HELP -> Component.translatable("bloodcontracts.gui.tab.help");
        };
    }

    public void update(BountyListPayload payload) {
        wanted = payload.wanted();
        history = payload.history();
        claimable = payload.claimable();
        clockOffset = payload.serverNow() - System.currentTimeMillis();
        loaded = true;
        payload.status().ifPresent(s -> {
            status = s;
            statusIsError = payload.statusIsError();
        });
        payload.wanted().forEach(t -> ClientSkins.remember(t.profile()));
        if (detail != null && target(detail) == null) {
            detail = null;
            picking = false;
        }
        if (selectedDelivery != null && claimable.stream().noneMatch(d -> d.id().equals(selectedDelivery))) {
            selectedDelivery = null;
        }
        page = Math.min(page, Math.max(0, pages() - 1));
        refreshWidgets();
    }

    private List<BountyView> mine() {
        return wanted.stream().flatMap(t -> t.bounties().stream()).filter(BountyView::mine).toList();
    }

    private @Nullable TargetView target(UUID id) {
        return wanted.stream().filter(t -> t.targetId().equals(id)).findFirst().orElse(null);
    }

    /** The viewer's own bounty on the detailed target (at most one per sponsor and target). */
    private Optional<BountyView> myBounty() {
        TargetView t = detail == null ? null : target(detail);
        return t == null ? Optional.empty() : t.bounties().stream().filter(BountyView::mine).findFirst();
    }

    private int perPage() {
        return COLS * GRID_ROWS;
    }

    private int pages() {
        int n = switch (tab) {
            case WANTED -> wanted.size();
            case HISTORY -> history.size();
            default -> 0;
        };
        return Math.max(1, (n + perPage() - 1) / perPage());
    }

    private void switchTab(Tab to) {
        if (to == Tab.CREATE) {
            openCreate();
            return;
        }
        tab = to;
        page = 0;
        scroll = 0;
        detail = null;
        picking = false;
        selectedDelivery = null;
        refreshWidgets();
    }

    private void openCreate() {
        PacketDistributor.sendToServer(BCPayloads.OpenCreateMenuRequest.INSTANCE);
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        refreshWidgets();
    }

    private void claim(Optional<UUID> delivery) {
        PacketDistributor.sendToServer(new BCPayloads.ClaimRewardRequest(delivery));
    }

    private Set<UUID> pickerExcluded() {
        Set<UUID> out = new java.util.HashSet<>();
        if (minecraft != null && minecraft.player != null) {
            out.add(minecraft.player.getUUID());
        }
        if (detail != null) {
            out.add(detail);
        }
        myBounty().flatMap(BountyView::hunters).ifPresent(list -> list.forEach(h -> out.add(h.id())));
        return out;
    }

    private int listedHunters() {
        return myBounty().flatMap(BountyView::hunters).map(List::size).orElse(0);
    }

    private void openPicker() {
        picker.selected.clear();
        pickerSearch.setValue("");
        pickerSearch.setFocused(true);
        picking = true;
        refreshWidgets();
    }

    private void confirmPicker() {
        myBounty().ifPresent(v -> {
            if (!picker.selected.isEmpty()) {
                PacketDistributor.sendToServer(new BCPayloads.AddHuntersRequest(v.id(), List.copyOf(picker.selected)));
            }
        });
        picking = false;
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (prev == null) {
            return;
        }
        tabs.forEach((tb, button) -> {
            button.setMessage(tabLabel(tb));
            button.selected = tb == tab;
            button.active = !picking;
        });
        tabs.get(Tab.CLAIM).attention = !claimable.isEmpty();
        boolean grid = detail == null && (tab == Tab.WANTED || tab == Tab.HISTORY);
        prev.visible = next.visible = grid && pages() > 1 && !picking;
        prev.active = page > 0;
        next.active = page < pages() - 1;
        back.visible = detail != null && !picking;
        newBounty.visible = detail == null && (tab == Tab.MINE || tab == Tab.WANTED);
        newBounty.active = mine().size() < ClientPayloadHandler.helpConfig.maxActiveBountiesPerSponsor();
        claimButton.visible = claimAllButton.visible = tab == Tab.CLAIM && detail == null;
        claimButton.active = selectedDelivery != null;
        claimAllButton.active = !claimable.isEmpty();

        Optional<BountyView> own = myBounty();
        boolean ownPrivate = own.map(v -> v.type() == BountyType.PRIVATE).orElse(false);
        addHunters.visible = makePublic.visible = detail != null && ownPrivate && !picking;
        addHunters.active = listedHunters() < ClientPayloadHandler.helpConfig.maxHunterListSize();
        boolean pending = own.map(v -> v.cancelAt().isPresent()).orElse(false);
        cancel.visible = detail != null && own.isPresent() && !pending && !picking;
        resume.visible = detail != null && own.isPresent() && pending && !picking;

    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        BCGui.panel(g, l, t, WIDTH, HEIGHT);
        g.fill(l + 10, t + 25, l + WIDTH - 10, t + 26, 0xFF3B2412);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        int mx = picking ? -1 : mouseX, my = picking ? -1 : mouseY;
        List<Component> tooltip = null;
        ItemStack itemTooltip = ItemStack.EMPTY;

        if (!loaded) {
            empty(g, Component.translatable("bloodcontracts.gui.loading"));
        } else if (detail != null && target(detail) != null) {
            Object hovered = renderDetail(g, target(detail), mx, my, partialTick);
            if (hovered instanceof ItemStack s) {
                itemTooltip = s;
            } else if (hovered instanceof List<?> lines) {
                tooltip = lines.stream().map(Component.class::cast).toList();
            }
        } else {
            switch (tab) {
                case WANTED -> renderWanted(g, mx, my, partialTick);
                case HISTORY -> tooltip = renderHistory(g, mx, my, partialTick);
                case MINE -> tooltip = renderMine(g, mx, my);
                case CLAIM -> itemTooltip = renderClaim(g, mx, my);
                case HELP -> renderHelp(g);
                default -> { }
            }
        }

        if ((tab == Tab.WANTED || tab == Tab.HISTORY) && detail == null && pages() > 1) {
            BCGui.centered(g, font, Component.literal((page + 1) + " / " + pages()), l + WIDTH / 2, t + HEIGHT - 21, BCGui.INK_LIGHT);
        }
        if (status != null && !picking) {
            String s = font.plainSubstrByWidth(status.getString(), 200);
            int sx = tab == Tab.CLAIM || detail != null ? l + WIDTH - 80 - font.width(s) : l + WIDTH / 2 - font.width(s) / 2;
            if ((tab == Tab.WANTED || tab == Tab.HISTORY) && detail == null && pages() > 1) {
                sx = l + WIDTH - 80 - font.width(s);
            }
            g.drawString(font, s, Math.max(l + 118, sx), t + HEIGHT - 21, statusIsError ? BCGui.BLOOD_BRIGHT : BCGui.SUCCESS, false);
        }
        if (picking) {
            renderPicker(g, mouseX, mouseY, partialTick);
        } else if (!itemTooltip.isEmpty()) {
            g.renderTooltip(font, itemTooltip, mouseX, mouseY);
        } else if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void empty(GuiGraphics g, Component text) {
        int l = left(), t = top();
        BCGui.drop(g, l + WIDTH / 2 - 8, t + 90);
        BCGui.centered(g, font, text, l + WIDTH / 2, t + 112, BCGui.INK_LIGHT);
    }

    private int gridX(int col) {
        return left() + (WIDTH - (COLS * WantedPoster.W + (COLS - 1) * GAP_X)) / 2 + col * (WantedPoster.W + GAP_X);
    }

    private int gridY(int row) {
        return top() + TOP + 2 + row * (WantedPoster.H + GAP_Y);
    }

    /** Index in the current page of the poster under the mouse, or -1. */
    private int posterAt(double mx, double my, int count) {
        for (int i = 0; i < count; i++) {
            int x = gridX(i % COLS), y = gridY(i / COLS);
            if (mx >= x && mx < x + WantedPoster.W && my >= y && my < y + WantedPoster.H) {
                return i;
            }
        }
        return -1;
    }

    /** Eases each poster toward straight-and-raised while hovered. */
    private float hoverProgress(UUID id, boolean hovered, float partialTick) {
        float p = hover.getOrDefault(id, 0f);
        p += (hovered ? 1 : -1) * 0.18f * Math.max(0.5f, partialTick + 0.5f);
        p = Math.max(0, Math.min(1, p));
        hover.put(id, p);
        return p;
    }

    private void renderWanted(GuiGraphics g, int mx, int my, float partialTick) {
        if (wanted.isEmpty()) {
            empty(g, Component.translatable("bloodcontracts.gui.board.empty"));
            return;
        }
        List<TargetView> shown = wanted.stream().skip((long) page * perPage()).limit(perPage()).toList();
        int over = posterAt(mx, my, shown.size());
        long now = now();
        // The hovered poster is drawn last, above its neighbours.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shown.size(); i++) {
                if ((i == over) != (pass == 1)) {
                    continue;
                }
                TargetView tv = shown.get(i);
                float p = hoverProgress(tv.targetId(), i == over, partialTick);
                float scale = 1 + 0.08f * p;
                float x = gridX(i % COLS) - WantedPoster.W * (scale - 1) / 2, y = gridY(i / COLS) - WantedPoster.H * (scale - 1) / 2;
                WantedPoster.begin(g, x, y, scale, WantedPoster.tilt(tv.targetId()) * (1 - p), pass * 50);
                WantedPoster.drawTarget(g, font, tv, now);
                g.pose().popPose();
            }
        }
    }

    private @Nullable List<Component> renderHistory(GuiGraphics g, int mx, int my, float partialTick) {
        if (history.isEmpty()) {
            empty(g, Component.translatable("bloodcontracts.command.no_history"));
            return null;
        }
        List<BountyView> shown = history.stream().skip((long) page * perPage()).limit(perPage()).toList();
        int over = posterAt(mx, my, shown.size());
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shown.size(); i++) {
                if ((i == over) != (pass == 1)) {
                    continue;
                }
                BountyView v = shown.get(i);
                float p = hoverProgress(v.id(), i == over, partialTick);
                float scale = 1 + 0.08f * p;
                float x = gridX(i % COLS) - WantedPoster.W * (scale - 1) / 2, y = gridY(i / COLS) - WantedPoster.H * (scale - 1) / 2;
                WantedPoster.begin(g, x, y, scale, WantedPoster.tilt(v.id()) * (1 - p), pass * 50);
                WantedPoster.drawHistory(g, font, v);
                g.pose().popPose();
            }
        }
        return over >= 0 ? historyTooltip(shown.get(over)) : null;
    }

    private List<Component> historyTooltip(BountyView v) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(v.targetName()).withStyle(s -> s.withColor(0xFF5555).withBold(true)));
        v.rewards().forEach(stack -> lines.add(Component.literal("  " + stack.getCount() + "× ").append(stack.getHoverName()).withStyle(s -> s.withColor(0xFFAA00))));
        lines.add(Component.translatable("bloodcontracts.type." + v.type().name().toLowerCase(java.util.Locale.ROOT)).withStyle(s -> s.withColor(0xAAAAAA)));
        lines.add((v.hunterName().isPresent()
                ? Component.translatable("bloodcontracts.command.info_claimed", v.hunterName().get(), fmt.format(new Date(v.closedAt())))
                : Component.translatable("bloodcontracts.gui.withdrawn_on", fmt.format(new Date(v.closedAt())))).withStyle(s -> s.withColor(0xAAAAAA)));
        return lines;
    }

    /** Detail of a target: big poster, every bounty on it, recurring pairs, sponsor actions. Returns a tooltip if any. */
    private @Nullable Object renderDetail(GuiGraphics g, TargetView tv, int mx, int my, float partialTick) {
        int l = left(), t = top();
        long now = now();
        WantedPoster.begin(g, l + 16, t + TOP + 4, 1.45f, WantedPoster.tilt(tv.targetId()) * 0.4f, 0);
        WantedPoster.drawTarget(g, font, tv, now);
        g.pose().popPose();
        int ry = t + TOP + 148;
        for (TargetView.RepeatView r : tv.repeatHunters()) {
            for (FormattedCharSequence line : font.split(Component.translatable("bloodcontracts.gui.repeat_pair", r.hunterName(), r.count(), tv.targetName()), 110)) {
                g.drawString(font, line, l + 14, ry, BCGui.INK_LIGHT, false);
                ry += 9;
            }
        }

        int x = l + 132, right = l + WIDTH - 14, headerY = t + TOP + 2;
        g.drawString(font, Component.translatable("bloodcontracts.gui.detail.header", tv.targetName(), tv.bounties().size())
                .withStyle(s -> s.withBold(true)), x, headerY, BCGui.BLOOD, false);
        tv.immuneUntil().filter(u -> u > now).ifPresent(u -> g.drawString(font,
                Component.translatable("bloodcontracts.gui.immune_for", Durations.of(u - now)), x, headerY + 11, WantedPoster.IMMUNE_INK, false));
        int y = headerY + 24;
        int bottom = t + BOTTOM - (myBounty().isPresent() ? 24 : 0);
        int rows = Math.max(1, (bottom - y) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, tv.bounties().size() - rows));
        Object hovered = null;
        for (int i = 0; i < rows && scroll + i < tv.bounties().size(); i++) {
            BountyView v = tv.bounties().get(scroll + i);
            int rowY = y + i * ROW_HEIGHT;
            BCGui.inset(g, x, rowY, right - x, ROW_HEIGHT - 2, v.mine());
            int icons = Math.min(4, v.rewards().size());
            for (int k = 0; k < icons; k++) {
                int ix = x + 4 + k * 18;
                BCGui.slot(g, ix, rowY + 5, true);
                g.renderItem(v.rewards().get(k), ix + 1, rowY + 6);
                g.renderItemDecorations(font, v.rewards().get(k), ix + 1, rowY + 6);
                if (mx >= ix && mx < ix + 18 && my >= rowY + 5 && my < rowY + 23) {
                    hovered = v.rewards().get(k);
                }
            }
            int tx = x + 8 + icons * 18 + (v.rewards().size() > 4 ? 10 : 0);
            if (v.rewards().size() > 4) {
                g.drawString(font, "+" + (v.rewards().size() - 4), tx - 12, rowY + 10, BCGui.BLOOD, false);
            }
            MutableComponent line1 = Component.translatable("bloodcontracts.type." + v.type().name().toLowerCase(java.util.Locale.ROOT))
                    .append(" · " + new SimpleDateFormat("dd/MM HH:mm").format(new Date(v.createdAt())));
            if (v.mine()) {
                line1.append(" ").append(Component.translatable("bloodcontracts.gui.mine"));
            } else {
                v.sponsorName().ifPresent(n -> line1.append(Component.translatable("bloodcontracts.command.by", n)));
            }
            g.drawString(font, font.plainSubstrByWidth(line1.getString(), right - tx - 4), tx, rowY + 4,
                    v.type() == BountyType.PRIVATE ? WantedPoster.PRIVATE_INK : BCGui.INK, false);
            Component line2;
            int color;
            if (v.cancelAt().isPresent()) {
                line2 = Component.translatable("bloodcontracts.gui.expires_in", Durations.of(v.cancelAt().get() - now));
                color = BCGui.BLOOD_BRIGHT;
            } else if (v.hunters().isPresent()) {
                line2 = Component.translatable("bloodcontracts.gui.hunters_count", v.hunters().get().size());
                color = WantedPoster.PRIVATE_INK;
            } else {
                line2 = Component.translatable(v.eligible() ? "bloodcontracts.gui.can_collect" : "bloodcontracts.gui.cannot_collect");
                color = v.eligible() ? BCGui.SUCCESS : BCGui.INK_LIGHT;
            }
            g.drawString(font, font.plainSubstrByWidth(line2.getString(), right - tx - 4), tx, rowY + 15, color, false);
            if (hovered == null && mx >= x && mx < right && my >= rowY && my < rowY + ROW_HEIGHT - 2 && v.hunters().isPresent()) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("bloodcontracts.gui.hunters").withStyle(s -> s.withColor(0xAAAAFF)));
                v.hunters().get().forEach(h -> lines.add(Component.literal("  " + h.name())));
                hovered = lines;
            }
        }
        BCGui.scrollbar(g, right + 2, y, rows * ROW_HEIGHT - 2, tv.bounties().size(), rows, scroll);
        return hovered;
    }

    private @Nullable List<Component> renderMine(GuiGraphics g, int mx, int my) {
        List<BountyView> list = mine();
        int l = left(), t = top();
        g.drawString(font, Component.translatable("bloodcontracts.gui.mine.header", list.size(),
                ClientPayloadHandler.helpConfig.maxActiveBountiesPerSponsor()), l + 14, t + TOP + 2, BCGui.INK, false);
        if (list.isEmpty()) {
            empty(g, Component.translatable("bloodcontracts.gui.mine.empty"));
            return null;
        }
        long now = now();
        List<Component> tooltip = null;
        for (int i = 0; i < LIST_ROWS && scroll + i < list.size(); i++) {
            BountyView v = list.get(scroll + i);
            int x = l + 12, y = t + TOP + 14 + i * ROW_HEIGHT, right = l + WIDTH - 18;
            boolean over = mx >= x && mx < right && my >= y && my < y + ROW_HEIGHT - 2;
            BCGui.inset(g, x, y, right - x, ROW_HEIGHT - 2, over);
            BCGui.face(g, v.targetId(), x + 5, y + 4, 20);
            g.drawString(font, Component.literal(v.targetName()).withStyle(s -> s.withBold(true)), x + 31, y + 4, BCGui.BLOOD, false);
            String rewards = v.rewards().stream().map(s -> s.getCount() + "× " + s.getHoverName().getString()).collect(Collectors.joining(", "));
            g.drawString(font, font.plainSubstrByWidth(rewards, 200), x + 31, y + 15, BCGui.AMBER, false);
            Component state;
            int color;
            if (v.cancelAt().isPresent()) {
                state = Component.translatable("bloodcontracts.gui.expires_in", Durations.of(v.cancelAt().get() - now));
                color = BCGui.BLOOD_BRIGHT;
            } else if (v.type() == BountyType.PRIVATE) {
                state = Component.translatable("bloodcontracts.gui.private_with", v.hunters().map(List::size).orElse(0));
                color = WantedPoster.PRIVATE_INK;
            } else {
                state = Component.translatable("bloodcontracts.type.public");
                color = BCGui.INK_LIGHT;
            }
            g.drawString(font, state, right - 6 - font.width(state), y + 4, color, false);
            if (over) {
                tooltip = List.of(Component.translatable("bloodcontracts.gui.hint.open_detail"));
            }
        }
        BCGui.scrollbar(g, l + WIDTH - 16, t + TOP + 14, LIST_ROWS * ROW_HEIGHT - 2, list.size(), LIST_ROWS, scroll);
        return tooltip;
    }

    private ItemStack renderClaim(GuiGraphics g, int mx, int my) {
        if (claimable.isEmpty()) {
            empty(g, Component.translatable("bloodcontracts.gui.claim.empty"));
            return ItemStack.EMPTY;
        }
        int l = left(), t = top();
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < LIST_ROWS && scroll + i < claimable.size(); i++) {
            DeliveryView d = claimable.get(scroll + i);
            int x = l + 12, y = t + TOP + 4 + i * ROW_HEIGHT, right = l + WIDTH - 18;
            BCGui.inset(g, x, y, right - x, ROW_HEIGHT - 2, d.id().equals(selectedDelivery));
            BCGui.slot(g, x + 5, y + 5, true);
            g.renderItem(d.stack(), x + 6, y + 6);
            g.renderItemDecorations(font, d.stack(), x + 6, y + 6);
            MutableComponent name = Component.literal(d.stack().getCount() + "× ").append(d.stack().getHoverName());
            g.drawString(font, name.withStyle(s -> s.withBold(true)), x + 30, y + 5, BCGui.AMBER, false);
            Component why = Component.translatable(d.reason() == PendingDelivery.Reason.REWARD
                    ? "bloodcontracts.gui.claim.reward" : "bloodcontracts.gui.claim.refund", d.targetName());
            g.drawString(font, why, x + 30, y + 16, BCGui.INK_LIGHT, false);
            if (mx >= x + 5 && mx < x + 23 && my >= y + 5 && my < y + 23) {
                hovered = d.stack();
            }
        }
        BCGui.scrollbar(g, l + WIDTH - 16, t + TOP + 4, LIST_ROWS * ROW_HEIGHT - 2, claimable.size(), LIST_ROWS, scroll);
        return hovered;
    }

    // ---------------------------------------------------------------- how it works

    private record HelpSection(ItemStack icon, Component title, List<Component> body) {}

    private List<HelpSection> helpSections() {
        BCPayloads.HelpConfigPayload c = ClientPayloadHandler.helpConfig;
        List<HelpSection> out = new ArrayList<>();
        out.add(new HelpSection(new ItemStack(Items.CHEST), Component.translatable("bloodcontracts.help.place.title"),
                List.of(Component.translatable("bloodcontracts.help.place.body", c.maxActiveBountiesPerSponsor()))));
        out.add(new HelpSection(new ItemStack(Items.MAP), Component.translatable("bloodcontracts.help.types.title"),
                List.of(Component.translatable("bloodcontracts.help.types.body", c.maxHunterListSize()),
                        Component.translatable("bloodcontracts.help.types.widen"))));
        out.add(new HelpSection(new ItemStack(Items.CLOCK), Component.translatable("bloodcontracts.help.cancel.title"),
                List.of(Component.translatable("bloodcontracts.help.cancel.body", Durations.ofMinutes(c.cancelNoticeMinutes())))));
        List<Component> kills = new ArrayList<>();
        kills.add(Component.translatable("bloodcontracts.help.kills.body"));
        if (c.creditTamedPets()) {
            kills.add(Component.translatable("bloodcontracts.help.kills.pets"));
        }
        kills.add(Component.translatable("bloodcontracts.help.kills.environment", c.attributionWindowTicks() / 20));
        out.add(new HelpSection(new ItemStack(Items.BOW), Component.translatable("bloodcontracts.help.kills.title"), kills));
        List<Component> hearts = new ArrayList<>();
        hearts.add(Component.translatable("bloodcontracts.help.hearts.body", c.maxHeartsLostPerDeath(), c.maxHeartsLost(), c.maxHeartsGained()));
        hearts.add(c.progressiveRegen()
                ? Component.translatable("bloodcontracts.help.hearts.regen", Durations.ofMinutes(c.regenOnlineStepMinutes()),
                        Durations.ofMinutes(c.regenOfflineStepMinutes()))
                : Component.translatable("bloodcontracts.help.hearts.permanent"));
        out.add(new HelpSection(new ItemStack(Items.GOLDEN_APPLE), Component.translatable("bloodcontracts.help.hearts.title"), hearts));
        out.add(new HelpSection(new ItemStack(Items.SHIELD), Component.translatable("bloodcontracts.help.immunity.title"),
                List.of(Component.translatable("bloodcontracts.help.immunity.body", Durations.ofMinutes(c.immunityMinutes())))));
        out.add(new HelpSection(new ItemStack(Items.ENDER_CHEST), Component.translatable("bloodcontracts.help.claim.title"),
                List.of(Component.translatable("bloodcontracts.help.claim.body"))));
        return out;
    }

    /** Laid-out help lines: an icon + title, then wrapped paragraphs. */
    private List<Object> helpLines(int width) {
        List<Object> lines = new ArrayList<>();
        for (HelpSection s : helpSections()) {
            lines.add(s);
            for (Component p : s.body()) {
                lines.addAll(font.split(p, width));
            }
            lines.add(Boolean.TRUE); // spacer
        }
        return lines;
    }

    private static final int HELP_LINE = 10;

    private int helpVisibleLines() {
        return (BOTTOM - TOP - 4) / HELP_LINE;
    }

    private void renderHelp(GuiGraphics g) {
        int l = left(), t = top();
        List<Object> lines = helpLines(WIDTH - 60);
        scroll = Math.max(0, Math.min(scroll, lines.size() - helpVisibleLines()));
        int y = t + TOP + 2;
        for (int i = scroll; i < lines.size() && i < scroll + helpVisibleLines(); i++, y += HELP_LINE) {
            Object line = lines.get(i);
            if (line instanceof HelpSection s) {
                g.renderItem(s.icon(), l + 16, y - 4);
                g.drawString(font, s.title().copy().withStyle(st -> st.withBold(true)), l + 36, y + 1, BCGui.BLOOD, false);
            } else if (line instanceof FormattedCharSequence seq) {
                g.drawString(font, seq, l + 36, y, BCGui.INK, false);
            }
        }
        BCGui.scrollbar(g, l + WIDTH - 16, t + TOP + 2, helpVisibleLines() * HELP_LINE, lines.size(), helpVisibleLines(), scroll);
    }

    // ---------------------------------------------------------------- picker

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int px = left() + (WIDTH - 200) / 2, py = top() + 30;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(left(), top(), left() + WIDTH, top() + HEIGHT, 0x90100804);
        BCGui.panel(g, px, py, 200, 188);
        BCGui.centered(g, font, Component.translatable("bloodcontracts.gui.add_hunters.title",
                picker.room(listedHunters())), px + 100, py + 8, BCGui.BLOOD);
        picker.render(g, font, px + 8, py + 36, 184, 126, mouseX, mouseY, listedHunters());
        pickerConfirm.active = !picker.selected.isEmpty();
        for (var widget : List.of(pickerSearch, pickerConfirm, pickerClose)) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picking) {
            if (picker.mouseClicked(mouseX, mouseY, listedHunters())) {
                return true;
            }
            pickerSearch.setFocused(pickerSearch.mouseClicked(mouseX, mouseY, button));
            if (!pickerSearch.isFocused()) {
                pickerConfirm.mouseClicked(mouseX, mouseY, button);
                pickerClose.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int l = left(), t = top();
        if (detail == null && tab == Tab.WANTED) {
            List<TargetView> shown = wanted.stream().skip((long) page * perPage()).limit(perPage()).toList();
            int i = posterAt(mouseX, mouseY, shown.size());
            if (i >= 0) {
                openDetail(shown.get(i).targetId());
                return true;
            }
        } else if (detail == null && tab == Tab.MINE) {
            List<BountyView> list = mine();
            for (int i = 0; i < LIST_ROWS && scroll + i < list.size(); i++) {
                int y = t + TOP + 14 + i * ROW_HEIGHT;
                if (mouseX >= l + 12 && mouseX < l + WIDTH - 18 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                    openDetail(list.get(scroll + i).targetId());
                    return true;
                }
            }
        } else if (detail == null && tab == Tab.CLAIM) {
            for (int i = 0; i < LIST_ROWS && scroll + i < claimable.size(); i++) {
                int y = t + TOP + 4 + i * ROW_HEIGHT;
                if (mouseX >= l + 12 && mouseX < l + WIDTH - 18 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                    UUID id = claimable.get(scroll + i).id();
                    selectedDelivery = id.equals(selectedDelivery) ? null : id;
                    refreshWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    private void openDetail(UUID target) {
        detail = target;
        scroll = 0;
        refreshWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picking) {
            return picker.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (detail == null && (tab == Tab.WANTED || tab == Tab.HISTORY)) {
            turn(-(int) Math.signum(scrollY));
            return true;
        }
        int total = detail != null ? Optional.ofNullable(target(detail)).map(tv -> tv.bounties().size()).orElse(0)
                : switch (tab) {
                    case MINE -> mine().size();
                    case CLAIM -> claimable.size();
                    case HELP -> helpLines(WIDTH - 60).size();
                    default -> 0;
                };
        int visible = tab == Tab.HELP && detail == null ? helpVisibleLines() : LIST_ROWS;
        scroll = Math.max(0, Math.min(Math.max(0, total - visible), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picking && keyCode == 256) {
            picking = false;
            refreshWidgets();
            return true;
        }
        if (picking) {
            return pickerSearch.keyPressed(keyCode, scanCode, modifiers) || pickerSearch.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picking) {
            return pickerSearch.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
