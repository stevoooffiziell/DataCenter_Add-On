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
        int[] col = Panel.columns(x, w, 0, 0.28, 0.42, 0.58, 0.72);
        panel.header(g, x, ty, w, col, "column.product", "column.pool", "column.reachable", "column.customers",
                "column.market_share");
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
            float share = row.pool() <= 0 ? 0 : (float) Math.min(1, row.customers() / row.pool());
            float reach = row.pool() <= 0 ? 0 : (float) Math.min(1, row.potential() / row.pool());
            int barW = x + w - col[4] - 50;
            TechPanel.bar(g, col[4], ty + 3, barW, 9, reach, Panel.HEADER_LINE);
            TechPanel.bar(g, col[4], ty + 3, (int) (barW * reach), 9, reach <= 0 ? 0 : share / reach, TechPanel.VIOLET);
            g.drawString(panel.font, Texts.percent(share), col[4] + barW + 6, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            if (panel.hover(col[1], ty, col[2] - col[1], ROW_H)) {
                panel.tooltip(List.of(Component.translatable("data_center_addon.market.pool.tip").withStyle(ChatFormatting.GRAY)));
            } else if (panel.hover(col[2], ty, col[3] - col[2], ROW_H)) {
                panel.tooltip(reachTooltip(row));
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
                Component.translatable("data_center_addon.market.reach.awareness.tip").withStyle(ChatFormatting.DARK_GRAY));
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
