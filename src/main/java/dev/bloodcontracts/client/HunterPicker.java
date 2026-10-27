package dev.bloodcontracts.client;

import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload.KnownPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Checkbox list of known players (faces, names), used to choose the hunters of a private bounty. Only a selection
 * helper: what the server receives is a list of UUIDs it validates again.
 */
final class HunterPicker {
    private static final int ROW = 13;

    final Set<UUID> selected = new LinkedHashSet<>();
    private final Supplier<Collection<UUID>> excluded;
    private String filter = "";
    private int scroll;
    private int x, y, w, h;

    /** @param excluded players that cannot be picked (sponsor, target, hunters already listed) */
    HunterPicker(Supplier<Collection<UUID>> excluded) {
        this.excluded = excluded;
    }

    void setFilter(String text) {
        filter = text.trim().toLowerCase(Locale.ROOT);
        scroll = 0;
    }

    /** How many more hunters can be added. */
    int room(int alreadyListed) {
        return Math.max(0, ClientPayloadHandler.helpConfig.maxHunterListSize() - alreadyListed - selected.size());
    }

    List<KnownPlayer> candidates() {
        Collection<UUID> out = excluded.get();
        selected.removeIf(out::contains);
        List<KnownPlayer> list = new ArrayList<>();
        for (KnownPlayer p : ClientPayloadHandler.knownPlayers) {
            if (!out.contains(p.id()) && (filter.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(filter))) {
                list.add(p);
            }
        }
        return list;
    }

    private int rows() {
        return Math.max(1, h / ROW);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, int alreadyListed) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        BCGui.inset(g, x, y, w, h, false);
        List<KnownPlayer> list = candidates();
        scroll = Math.max(0, Math.min(scroll, list.size() - rows()));
        if (list.isEmpty()) {
            BCGui.centered(g, font, Component.translatable("bloodcontracts.gui.no_candidates"), x + w / 2, y + h / 2 - 4, BCGui.INK_LIGHT);
        }
        boolean full = room(alreadyListed) <= 0;
        for (int i = 0; i < rows() && scroll + i < list.size(); i++) {
            KnownPlayer p = list.get(scroll + i);
            int ry = y + 1 + i * ROW;
            boolean on = selected.contains(p.id());
            boolean disabled = !on && full;
            if (mouseX >= x && mouseX < x + w - 5 && mouseY >= ry && mouseY < ry + ROW && !disabled) {
                g.fill(x + 1, ry, x + w - 5, ry + ROW, 0x20000000);
            }
            g.fill(x + 3, ry + 2, x + 12, ry + 11, BCGui.INK);
            g.fill(x + 4, ry + 3, x + 11, ry + 10, disabled ? 0xFF9C8C70 : 0xFFE8D8B4);
            if (on) {
                g.fill(x + 5, ry + 4, x + 10, ry + 9, BCGui.BLOOD);
            }
            BCGui.face(g, p.id(), x + 15, ry + 2, 9);
            String name = font.plainSubstrByWidth(p.name(), w - 34);
            g.drawString(font, name, x + 27, ry + 3, disabled ? BCGui.INK_LIGHT : (on ? BCGui.BLOOD : BCGui.INK), false);
        }
        BCGui.scrollbar(g, x + w - 5, y + 1, h - 2, list.size(), rows(), scroll);
    }

    boolean mouseClicked(double mx, double my, int alreadyListed) {
        if (mx < x || mx >= x + w - 5 || my < y || my >= y + h) {
            return false;
        }
        int i = (int) ((my - y - 1) / ROW);
        List<KnownPlayer> list = candidates();
        if (i >= 0 && i < rows() && scroll + i < list.size()) {
            UUID id = list.get(scroll + i).id();
            if (!selected.remove(id) && room(alreadyListed) > 0) {
                selected.add(id);
            }
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        int max = Math.max(0, candidates().size() - rows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }
}
