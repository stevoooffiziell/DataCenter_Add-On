package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.hosting.CustomerClass;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/**
 * Overview: income cards (a real hour as in the rack's own menu, a game day, a month; with the running
 * start bonus), reputation, network, warnings, and a table by product in which running contracts count
 * with the product whose places they use.
 */
final class OverviewTab implements ManagerTab {
    private static final int CARD_H = 36;

    private final Panel panel;

    OverviewTab(Panel panel) {
        this.panel = panel;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int ny = cards(g, s, x, y, w);
        ny = reputationAndNetwork(g, s, x, ny, w);
        products(g, s, x, ny + 22, w, y + h);
    }

    /** The five cards and the start bonus line; returns the y below them. */
    private int cards(GuiGraphics g, HostingSnapshot s, int x, int y, int w) {
        long bonusPerDay = s.startBonusCentsPerDay();
        long perDay = s.incomeCentsPerDay() + bonusPerDay;
        String[][] cards = {
                {Texts.hosting("overview.customers"), GuiText.compactNumber(Math.round(s.totalCustomers()))},
                {Texts.hosting("overview.per_hour"), GuiText.compactUsd(s.perHour(perDay))},
                {Texts.hosting("overview.per_day"), GuiText.compactUsd(perDay)},
                {Texts.hosting("overview.per_month"), GuiText.compactUsd(perDay * 30)},
                {Texts.hosting("overview.racks"), s.racks().size() + (s.foreignRacks() > 0 ? " (+" + s.foreignRacks() + ")" : "")},
        };
        int gap = 6, cardW = (w - (cards.length - 1) * gap) / cards.length;
        for (int i = 0; i < cards.length; i++) {
            int bx = x + i * (cardW + gap);
            boolean money = i >= 1 && i <= 3;
            g.fill(bx, y, bx + cardW, y + CARD_H, TechPanel.SLOT_BG);
            g.fill(bx, y, bx + 2, y + CARD_H, TechPanel.ACCENT);
            panel.line(g, cards[i][0], bx + 8, y + 6, cardW - 12, TechPanel.TEXT_MUTED);
            panel.line(g, cards[i][1], bx + 8, y + 20, cardW - 12, money ? TechPanel.GOOD : TechPanel.TEXT);
            if (money && panel.hover(bx, y, cardW, CARD_H)) {
                panel.tooltip(List.of(Component.literal(cards[i][0] + ": " + cards[i][1]),
                        Component.translatable("data_center_addon.hosting.overview.time_note").withStyle(ChatFormatting.GRAY)));
            }
        }
        int below = y + CARD_H + 12;
        if (bonusPerDay > 0) {
            String bonus = Component.translatable("data_center_addon.hosting.overview.bonus", Texts.bonus(s.startup().bonusFactor()),
                    GuiText.compactUsd(s.perHour(bonusPerDay)), GuiText.compactUsd(bonusPerDay)).getString();
            panel.line(g, bonus, x, y + CARD_H + 4, w, TechPanel.GOOD);
            below += 8;
        }
        return below;
    }

    /** Reputation and network bars (label | bar | value) and the warnings; returns the y of the last line. */
    private int reputationAndNetwork(GuiGraphics g, HostingSnapshot s, int x, int ry, int w) {
        int labelW = 78, valueW = Math.max(130, w / 3);
        int barX = x + labelW, barW = w - labelW - valueW - 8;
        panel.line(g, Texts.hosting("overview.reputation"), x, ry + 1, labelW, TechPanel.TEXT_MUTED);
        TechPanel.bar(g, barX, ry, barW, 10, s.reputation() / 1000f, TechPanel.ACCENT);
        String rep = s.reputation() + " / 1000 · " + Component.translatable(CustomerClass.ofOrdinal(s.customerClass()).translationKey()).getString();
        panel.line(g, rep, barX + barW + 8, ry + 1, valueW, TechPanel.TEXT);

        int ny = ry + 16;
        HostingSnapshot.Network net = s.network();
        panel.line(g, Texts.hosting("overview.network"), x, ny + 1, labelW, TechPanel.TEXT_MUTED);
        boolean limited = net.capacity() != Long.MAX_VALUE;
        float used = !limited ? 0 : net.capacity() <= 0 ? 1 : (float) net.delivered() / net.capacity();
        TechPanel.bar(g, barX, ny, barW, 10, limited ? used : 0, used > 0.95f ? TechPanel.WARN : TechPanel.OUTPUT);
        String netText = GuiText.compactNumber(net.delivered()) + " / " + (limited ? GuiText.compactNumber(net.capacity()) : "∞")
                + " " + Texts.hosting("overview.compute");
        panel.line(g, netText, barX + barW + 8, ny + 1, valueW, TechPanel.TEXT);

        if (net.unpoweredSwitches() > 0 || net.overlongRuns() > 0) {
            ny += 14;
            Component warning = net.unpoweredSwitches() > 0
                    ? Component.translatable("data_center_addon.hosting.overview.unpowered", net.unpoweredSwitches())
                    : Component.translatable("data_center_addon.hosting.overview.overlong", net.overlongRuns());
            panel.line(g, "⚠ " + warning.getString(), x, ny + 1, w, net.unpoweredSwitches() > 0 ? TechPanel.DANGER : TechPanel.WARN);
        }
        long waiting = Math.round(s.market().waiting());
        if (waiting > 0) {
            ny += 14;
            panel.line(g, "⚠ " + Component.translatable("data_center_addon.hosting.overview.waiting", GuiText.compactNumber(waiting))
                    .getString(), x, ny + 1, w, TechPanel.WARN);
        }
        int licenceBound = s.licenceBoundRacks();
        if (licenceBound > 0) {
            ny += 14;
            Component warning = Component.literal("⚠ ")
                    .append(Component.translatable("data_center_addon.hosting.overview.licence", licenceBound));
            List<FormattedCharSequence> lines = panel.font.split(warning, w);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                g.drawString(panel.font, lines.get(i), x, ny + 1 + i * 10, TechPanel.WARN, false);
            }
            ny += Math.min(lines.size(), 2) * 10 - 10;
        }
        return ny;
    }

    /** Sums of one product row. */
    private static final class ProductRow {
        int racks;
        double customers, capacity;
        long centsPerDay;
        /** Places reserved by running contracts and what they pay. */
        int contractPlaces;
        long contractCentsPerDay;
    }

    /** The table by product: racks, customers (+ contract places in blue), load and income. */
    private void products(GuiGraphics g, HostingSnapshot s, int x, int ty, int w, int bottom) {
        int[] col = Panel.columns(x, w, 0, 0.40, 0.52, 0.66, 0.86);
        panel.header(g, x, ty, w, col, "column.product", "column.racks", "column.customers", "column.load", "column.per_day");
        ty += ROW_H + 2;

        Map<Integer, ProductRow> byProduct = new LinkedHashMap<>();
        for (HostingSnapshot.Rack rack : s.racks()) {
            ProductRow row = byProduct.computeIfAbsent(rack.product(), p -> new ProductRow());
            row.racks++;
            row.customers += rack.customers();
            row.capacity += rack.capacity();
            row.centsPerDay += rack.centsPerDay();
        }
        for (HostingSnapshot.Contract c : s.activeContracts()) {
            ProductRow row = byProduct.computeIfAbsent(c.product(), p -> new ProductRow());
            row.contractPlaces += c.accounts();
            row.contractCentsPerDay += c.centsPerDay();
        }

        int index = 0;
        for (Map.Entry<Integer, ProductRow> entry : byProduct.entrySet()) {
            if (ty + ROW_H > bottom) {
                break;
            }
            ProductRow row = entry.getValue();
            Panel.stripe(g, x, ty, w, index++);
            panel.cell(g, Texts.productName(entry.getKey()).getString(), col, 0, ty, TechPanel.TEXT);
            panel.cell(g, Integer.toString(row.racks), col, 1, ty, TechPanel.TEXT);
            String customers = GuiText.compactNumber(Math.round(row.customers));
            g.drawString(panel.font, customers, col[2], ty + TEXT_DY, TechPanel.TEXT, false);
            if (row.contractPlaces > 0) {
                g.drawString(panel.font, " + " + GuiText.compactNumber(row.contractPlaces), col[2] + panel.font.width(customers),
                        ty + TEXT_DY, TechPanel.ACCENT, false);
                if (panel.hover(col[2], ty, col[3] - col[2], ROW_H)) {
                    panel.tooltip(List.of(Component.translatable("data_center_addon.hosting.overview.contract_places",
                            GuiText.compactNumber(row.contractPlaces), GuiText.compactUsd(row.contractCentsPerDay))));
                }
            }
            Panel.loadBar(g, col[3], ty + 3, col[4] - col[3] - 12,
                    row.capacity <= 0 ? 0 : (float) ((row.customers + row.contractPlaces) / row.capacity));
            panel.cell(g, GuiText.compactUsd(row.centsPerDay + row.contractCentsPerDay), col, 4, ty, TechPanel.GOOD);
            ty += ROW_H;
        }
    }
}
