package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.market.MarketEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/**
 * Market: per product the pool of potential customers, the part the account reaches, its customers, new
 * customers and cancellations, and the account's price.
 */
final class MarketTab implements ManagerTab {
    private final Panel panel;
    private final Supplier<BlockPos> manager;

    MarketTab(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        HostingSnapshot.Market m = s.market();
        String internet = Component.translatable("data_center_addon.market.internet", users(m.internetUsers())).getString();
        int internetW = panel.font.width(internet);
        panel.line(g, internet, x, y, w, TechPanel.TEXT_MUTED);
        if (panel.hover(x, y - 2, internetW, 12)) {
            panel.tooltip(List.of(Component.translatable("data_center_addon.market.internet.tip").withStyle(ChatFormatting.GRAY)));
        }
        if (m.phase() > 0) {
            String phase = Texts.market("event." + MarketEvents.Phase.values()[Math.min(m.phase(), MarketEvents.Phase.values().length - 1)]
                    .name().toLowerCase(Locale.ROOT));
            String event = Component.translatable("data_center_addon.market.event", phase, factor(m.mood())).getString();
            int ex = x + internetW + 12;
            panel.line(g, event, ex, y, x + w - ex, m.mood() < 1 ? TechPanel.WARN : TechPanel.GOOD);
            if (panel.hover(ex, y - 2, x + w - ex, 12)) {
                panel.tooltip(List.of(Component.literal(event),
                        Component.translatable("data_center_addon.market.event.tip").withStyle(ChatFormatting.GRAY)));
            }
        }
        int rivalsH = m.rivals().isEmpty() ? 0 : 30;
        rivals(g, m, x, y + h - rivalsH + 4, w);
        h -= rivalsH;
        int summaryY = y + h - 9;
        h -= 14;
        int ty = y + 14;
        int[] col = Panel.columns(x, w, 0, 0.22, 0.33, 0.45, 0.57, 0.68, 0.80);
        panel.header(g, x, ty, w, col, "column.product", "column.pool", "column.reachable", "column.customers",
                "column.inflow", "column.churn", "column.price");
        ty += ROW_H + 2;
        int index = 0;
        // the summary line follows the row under the mouse, otherwise the product with the most customers
        HostingSnapshot.MarketRow hovered = null, largest = null;
        for (HostingSnapshot.MarketRow row : m.rows()) {
            if (ty + ROW_H > y + h) {
                break;
            }
            if (panel.row(g, x, ty, w, index++, false)) {
                hovered = row;
            }
            if (largest == null || row.customers() > largest.customers()) {
                largest = row;
            }
            panel.cell(g, Texts.productName(row.product()).getString(), col, 0, ty, TechPanel.TEXT);
            panel.cell(g, GuiText.compactNumber(Math.round(row.pool())), col, 1, ty, TechPanel.TEXT);
            panel.cell(g, GuiText.compactNumber(Math.round(row.potential())), col, 2, ty, TechPanel.ACCENT);
            String customers = GuiText.compactNumber(Math.round(row.customers()));
            g.drawString(panel.font, customers, col[3], ty + TEXT_DY, TechPanel.TEXT, false);
            if (row.waiting() >= 0.5) {
                g.drawString(panel.font, " +" + GuiText.compactNumber(Math.round(row.waiting())), col[3] + panel.font.width(customers),
                        ty + TEXT_DY, TechPanel.WARN, false);
            }
            panel.cell(g, "+" + GuiText.compactNumber(Math.round(row.orders())), col, 4, ty, TechPanel.GOOD);
            HostingSnapshot.Quality q = row.quality();
            panel.cell(g, Texts.percent((float) q.churn()), col, 5, ty, churnColor(q));
            price(g, m, row, col[6], ty, x + w - col[6]);
            if (panel.hover(col[1], ty, col[2] - col[1], ROW_H)) {
                panel.tooltip(poolTooltip(row));
            } else if (panel.hover(col[2], ty, col[3] - col[2], ROW_H)) {
                panel.tooltip(reachTooltip(m, row));
            } else if (panel.hover(col[3], ty, col[4] - col[3], ROW_H)) {
                panel.tooltip(customersTooltip(row));
            } else if (panel.hover(col[4], ty, col[5] - col[4], ROW_H)) {
                panel.tooltip(inflowTooltip(row));
            } else if (panel.hover(col[5], ty, col[6] - col[5], ROW_H)) {
                panel.tooltip(qualityTooltip(q));
            }
            ty += ROW_H;
        }
        HostingSnapshot.MarketRow summary = hovered != null ? hovered : largest;
        if (summary != null) {
            panel.line(g, Texts.marketSummary(summary).getString(), x, summaryY, w, TechPanel.TEXT);
        }
    }

    /** The competitors in one line under the table; hover for the products each sells. */
    private void rivals(GuiGraphics g, HostingSnapshot.Market m, int x, int y, int w) {
        if (m.rivals().isEmpty()) {
            return;
        }
        panel.sectionTitle(g, x, y, w, Texts.market("rivals"));
        StringBuilder line = new StringBuilder();
        for (HostingSnapshot.Rival r : m.rivals()) {
            if (!line.isEmpty()) {
                line.append("  ·  ");
            }
            line.append(r.name()).append(' ').append(Math.round(r.strength() * 100)).append(" %");
        }
        panel.line(g, line.toString(), x, y + 14, w, TechPanel.TEXT_MUTED);
        if (panel.hover(x, y, w, 24)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("data_center_addon.market.rivals.tip").withStyle(ChatFormatting.GRAY));
            HostingProduct[] products = HostingProduct.values();
            for (HostingSnapshot.Rival r : m.rivals()) {
                StringBuilder sells = new StringBuilder();
                for (HostingProduct p : products) {
                    if ((r.products() & (1 << p.ordinal())) != 0) {
                        sells.append(sells.isEmpty() ? "" : ", ").append(Component.translatable(p.translationKey()).getString());
                    }
                }
                lines.add(Component.literal(r.name() + " " + Math.round(r.strength() * 100) + " %: " + sells));
            }
            panel.tooltip(lines);
        }
    }

    /** "◀ +10 % ▶": the account's price of the product; only with the market model on. */
    private void price(GuiGraphics g, HostingSnapshot.Market m, HostingSnapshot.MarketRow row, int px, int ty, int pw) {
        String value = row.priceStep() == 0 ? "±0 %" : String.format(Locale.ROOT, "%+d %%", row.priceStep() * 10);
        if (!m.model()) {
            panel.line(g, "–", px, ty + TEXT_DY, pw, TechPanel.TEXT_MUTED);
            return;
        }
        panel.stepper(g, px, ty - 1, Math.min(pw, 78), value,
                () -> Panel.send(new HostingManagerPayloads.PriceChange(manager.get(), row.product(), -1)),
                () -> Panel.send(new HostingManagerPayloads.PriceChange(manager.get(), row.product(), 1)));
        if (panel.hover(px + 16, ty, Math.min(pw, 78) - 32, ROW_H)) {
            panel.tooltip(List.of(Component.translatable("data_center_addon.market.price.title", value),
                    Component.translatable("data_center_addon.market.price.pay", factor(1 + row.priceStep() * 0.1))
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("data_center_addon.market.price.share", factor(row.priceFactor()))
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("data_center_addon.market.price.tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** Customers, market share and the waiting list. */
    private static List<Component> customersTooltip(HostingSnapshot.MarketRow row) {
        List<Component> lines = new ArrayList<>();
        float share = row.pool() <= 0 ? 0 : (float) Math.min(1, row.customers() / row.pool());
        lines.add(Component.translatable("data_center_addon.market.customers", GuiText.compactNumber(Math.round(row.customers())),
                Texts.percent(share)));
        if (row.waiting() >= 0.5) {
            lines.add(Component.translatable("data_center_addon.market.waiting", GuiText.compactNumber(Math.round(row.waiting())))
                    .withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** The market and the segments raised by launches in the world. */
    private static List<Component> poolTooltip(HostingSnapshot.MarketRow row) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("data_center_addon.market.pool.tip").withStyle(ChatFormatting.GRAY));
        if (Math.abs(row.market() - 1) >= 0.005) {
            lines.add(Component.translatable("data_center_addon.market.pool.events", factor(row.market()))
                    .withStyle(row.market() < 1 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
        CustomerSegment[] segments = CustomerSegment.values();
        for (HostingSnapshot.SegmentBoost b : row.boosts()) {
            Component segment = Component.translatable(segments[Mth.clamp(b.segment(), 0, segments.length - 1)].translationKey());
            lines.add(b.latest().isEmpty()
                    ? Component.translatable("data_center_addon.market.segment", segment, factor(b.factor())).withStyle(ChatFormatting.AQUA)
                    : Component.translatable("data_center_addon.market.segment.latest", segment, factor(b.factor()), b.latest(), b.year())
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    /** How the reachable part of the pool comes together. */
    private static List<Component> reachTooltip(HostingSnapshot.Market m, HostingSnapshot.MarketRow row) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("data_center_addon.market.reach.title", GuiText.compactNumber(Math.round(row.potential()))));
        lines.add(gray("data_center_addon.market.reach.base", GuiText.compactNumber(Math.round(row.pool()))));
        lines.add(gray("data_center_addon.market.reach.rivals", Texts.percent((float) row.competition()), factor(row.rivalFactor())));
        lines.add(gray("data_center_addon.market.reach.reputation", factor(row.repFactor())));
        lines.add(gray("data_center_addon.market.reach.classes", factor(row.classFactor())));
        lines.add(gray("data_center_addon.market.reach.awareness", factor(row.awareFactor())));
        lines.add(gray("data_center_addon.market.reach.price", factor(row.priceFactor())));
        lines.add(gray("data_center_addon.market.reach.ruf", factor(row.rufFactor())));
        if (m.shared()) {
            lines.add(gray("data_center_addon.market.reach.shared", row.accounts(), factor(row.sharing())));
        }
        lines.add(Component.translatable("data_center_addon.market.reach.classes.tip").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("data_center_addon.market.reach.awareness.tip").withStyle(ChatFormatting.DARK_GRAY));
        if (m.shared()) {
            lines.add(Component.translatable("data_center_addon.market.reach.shared.tip").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static Component gray(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    /** Where the new customers per month come from. */
    private static List<Component> inflowTooltip(HostingSnapshot.MarketRow row) {
        return List.of(
                Component.translatable("data_center_addon.market.inflow.title", GuiText.compactNumber(Math.round(row.orders()))),
                Component.translatable("data_center_addon.market.inflow.gap", GuiText.compactNumber(Math.round(row.ordersGap())))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.inflow.word", GuiText.compactNumber(Math.round(row.ordersWord())))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.inflow.note").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Green for good service, amber for middling, red for bad. */
    private static int churnColor(HostingSnapshot.Quality q) {
        if (!q.measured()) {
            return TechPanel.TEXT_MUTED;
        }
        return q.churn() <= 0.025 ? TechPanel.GOOD : q.churn() <= 0.05 ? TechPanel.WARN : TechPanel.DANGER;
    }

    /** What the cancellations come from. */
    private static List<Component> qualityTooltip(HostingSnapshot.Quality q) {
        if (!q.measured()) {
            return List.of(Component.translatable("data_center_addon.market.quality.title", Texts.percent((float) q.churn())),
                    Component.translatable("data_center_addon.market.quality.unmeasured").withStyle(ChatFormatting.GRAY));
        }
        List<Component> lines = new ArrayList<>();
        if (q.ruf() >= 0) {
            lines.add(Component.translatable("data_center_addon.market.quality.ruf", Texts.percent((float) q.ruf()))
                    .withStyle(ChatFormatting.AQUA));
        }
        lines.addAll(List.of(Component.translatable("data_center_addon.market.quality.title", Texts.percent((float) q.churn())),
                Component.translatable("data_center_addon.market.quality.down", Texts.percent((float) q.down())).withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.overload", Texts.percent((float) q.overload()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.throttle", Texts.percent((float) q.throttle()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.note").withStyle(ChatFormatting.DARK_GRAY)));
        return lines;
    }

    /** "×1.25" */
    static String factor(double f) {
        return String.format(Locale.ROOT, "×%.2f", f);
    }

    /** "1.75 bn" / "780 M" */
    private static String users(double millions) {
        return millions >= 1000
                ? String.format(Locale.ROOT, "%.2f %s", millions / 1000, Texts.market("billion"))
                : String.format(Locale.ROOT, "%.0f %s", millions, Texts.market("million"));
    }
}
