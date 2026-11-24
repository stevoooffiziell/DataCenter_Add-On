package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.hosting.CustomerSegment;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.contract.ContractService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/** Customers: the customer segments of the racks, then one row per running contract, with share and income. */
final class CustomersTab implements ManagerTab {
    private final Panel panel;

    CustomersTab(Panel panel) {
        this.panel = panel;
    }

    private record Row(String name, double customers, long centsPerDay, boolean contract) {
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int[] col = Panel.columns(x, w, 0, 0.40, 0.56, 0.84);
        panel.header(g, x, y, w, col, "column.segment", "column.customers", "column.share", "column.per_day");
        List<Row> rows = rows(s);
        if (rows.isEmpty()) {
            panel.message(g, x, y + ROW_H, w, h - ROW_H, Component.translatable("data_center_addon.hosting.no_segments"));
            return;
        }
        double total = rows.stream().mapToDouble(Row::customers).sum();
        int noteH = 14;
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - noteH) / ROW_H;
        panel.clampScroll(rows.size(), visible);
        int ty = top;
        int shareBarW = col[3] - col[2] - 56;
        for (int i = panel.scroll; i < rows.size() && i < panel.scroll + visible; i++) {
            Row r = rows.get(i);
            float share = total <= 0 ? 0 : (float) (r.customers / total);
            Panel.stripe(g, x, ty, w, i);
            panel.cell(g, r.name, col, 0, ty, r.contract ? TechPanel.ACCENT : TechPanel.TEXT);
            panel.cell(g, GuiText.compactNumber(Math.round(r.customers)), col, 1, ty, TechPanel.TEXT);
            TechPanel.bar(g, col[2], ty + 3, shareBarW, 9, share, r.contract ? TechPanel.ACCENT : TechPanel.VIOLET);
            g.drawString(panel.font, Texts.percent(share), col[2] + shareBarW + 6, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            panel.cell(g, GuiText.compactUsd(r.centsPerDay), col, 3, ty, TechPanel.GOOD);
            ty += ROW_H;
        }
        panel.scrollbar(g, x + w + 3, top, visible * ROW_H, rows.size(), visible);
        panel.line(g, Texts.hosting("segments.note"), x, y + h - 9, w, TechPanel.TEXT_MUTED);
    }

    private static List<Row> rows(HostingSnapshot s) {
        List<Row> rows = new ArrayList<>();
        CustomerSegment[] segments = CustomerSegment.values();
        for (HostingSnapshot.Segment segment : s.segments()) {
            CustomerSegment kind = segments[Mth.clamp(segment.segment(), 0, segments.length - 1)];
            rows.add(new Row(Component.translatable(kind.translationKey()).getString(), segment.customers(), segment.centsPerDay(), false));
        }
        for (HostingSnapshot.Contract c : s.activeContracts()) {
            rows.add(new Row(Component.translatable("data_center_addon.hosting.segments.contract",
                    ContractService.clientName(c.client())).getString(), c.accounts(), c.centsPerDay(), true));
        }
        return rows;
    }
}
