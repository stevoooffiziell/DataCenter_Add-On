package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/** Market: per product the pool of potential customers, the part the account reaches and its customers. */
final class MarketTab implements ManagerTab {
    private final Panel panel;

    MarketTab(Panel panel) {
        this.panel = panel;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        HostingSnapshot.Market m = s.market();
        String internet = Component.translatable("data_center_addon.market.internet", users(m.internetUsers())).getString();
        panel.line(g, internet, x, y, w, TechPanel.TEXT_MUTED);
        if (panel.hover(x, y - 2, w, 12)) {
            panel.tooltip(List.of(Component.translatable("data_center_addon.market.internet.tip").withStyle(ChatFormatting.GRAY)));
        }
        int ty = y + 14;
        int[] col = Panel.columns(x, w, 0, 0.22, 0.33, 0.45, 0.56, 0.68, 0.81);
        panel.header(g, x, ty, w, col, "column.product", "column.pool", "column.reachable", "column.customers",
                "column.inflow", "column.churn", "column.market_share");
        ty += ROW_H + 2;
        int index = 0;
        for (HostingSnapshot.MarketRow row : m.rows()) {
            if (ty + ROW_H > y + h) {
                break;
            }
            Panel.stripe(g, x, ty, w, index++);
            panel.cell(g, Texts.productName(row.product()).getString(), col, 0, ty, TechPanel.TEXT);
            panel.cell(g, GuiText.compactNumber(Math.round(row.pool())), col, 1, ty, TechPanel.TEXT);
            panel.cell(g, GuiText.compactNumber(Math.round(row.potential())), col, 2, ty, TechPanel.ACCENT);
            panel.cell(g, GuiText.compactNumber(Math.round(row.customers())), col, 3, ty, TechPanel.TEXT);
            panel.cell(g, "+" + GuiText.compactNumber(Math.round(row.orders())), col, 4, ty, TechPanel.GOOD);
            HostingSnapshot.Quality q = row.quality();
            panel.cell(g, Texts.percent((float) q.churn()), col, 5, ty, churnColor(q));
            float share = row.pool() <= 0 ? 0 : (float) Math.min(1, row.customers() / row.pool());
            float reach = row.pool() <= 0 ? 0 : (float) Math.min(1, row.potential() / row.pool());
            int barW = x + w - col[6] - 46;
            TechPanel.bar(g, col[6], ty + 3, barW, 9, reach, Panel.HEADER_LINE);
            TechPanel.bar(g, col[6], ty + 3, (int) (barW * reach), 9, reach <= 0 ? 0 : share / reach, TechPanel.VIOLET);
            g.drawString(panel.font, Texts.percent(share), col[6] + barW + 4, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            if (panel.hover(col[1], ty, col[2] - col[1], ROW_H)) {
                panel.tooltip(List.of(Component.translatable("data_center_addon.market.pool.tip").withStyle(ChatFormatting.GRAY)));
            } else if (panel.hover(col[2], ty, col[3] - col[2], ROW_H)) {
                panel.tooltip(reachTooltip(row));
            } else if (panel.hover(col[4], ty, col[5] - col[4], ROW_H)) {
                panel.tooltip(inflowTooltip(row));
            } else if (panel.hover(col[5], ty, col[6] - col[5], ROW_H)) {
                panel.tooltip(qualityTooltip(q));
            }
            ty += ROW_H;
        }
    }

    /** How the reachable part of the pool comes together. */
    private static List<Component> reachTooltip(HostingSnapshot.MarketRow row) {
        return List.of(
                Component.translatable("data_center_addon.market.reach.title", GuiText.compactNumber(Math.round(row.potential()))),
                Component.translatable("data_center_addon.market.reach.base", GuiText.compactNumber(Math.round(row.pool())))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.reach.reputation", factor(row.repFactor())).withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.reach.awareness", factor(row.awareFactor())).withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.reach.classes", factor(row.classFactor())).withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.reach.classes.tip").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("data_center_addon.market.reach.awareness.tip").withStyle(ChatFormatting.DARK_GRAY));
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
        return List.of(Component.translatable("data_center_addon.market.quality.title", Texts.percent((float) q.churn())),
                Component.translatable("data_center_addon.market.quality.down", Texts.percent((float) q.down())).withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.overload", Texts.percent((float) q.overload()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.throttle", Texts.percent((float) q.throttle()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("data_center_addon.market.quality.note").withStyle(ChatFormatting.DARK_GRAY));
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
