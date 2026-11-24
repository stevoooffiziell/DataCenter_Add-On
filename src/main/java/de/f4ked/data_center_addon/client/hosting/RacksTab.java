package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingManagerService;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/**
 * Racks: every rack with product, customers / places offered, share, income and status; the selected
 * rack's product and fill can be changed like in the rack's own menu.
 */
final class RacksTab implements ManagerTab {
    private static final int CONTROLS_H = 50;

    private final Panel panel;
    private final Supplier<BlockPos> manager;
    @Nullable
    private BlockPos selected;

    RacksTab(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int[] col = Panel.columns(x, w, 0, 0.19, 0.44, 0.60, 0.71, 0.84);
        panel.header(g, x, y, w, col, "column.rack", "column.product", "column.customers", "column.share", "column.per_day", "column.status");
        double total = s.totalCustomers();
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - CONTROLS_H) / ROW_H;
        List<HostingSnapshot.Rack> racks = s.racks();
        panel.clampScroll(racks.size(), visible);
        HostingSnapshot.Rack current = null;
        for (HostingSnapshot.Rack rack : racks) {
            if (rack.pos().equals(selected)) {
                current = rack;
            }
        }
        int ty = top;
        for (int i = panel.scroll; i < racks.size() && i < panel.scroll + visible; i++) {
            rackRow(g, racks.get(i), x, ty, w, col, i, total);
            ty += ROW_H;
        }
        panel.scrollbar(g, x + w + 3, top, visible * ROW_H, racks.size(), visible);
        controls(g, current, x, y + h - CONTROLS_H + 8, w);
    }

    private void rackRow(GuiGraphics g, HostingSnapshot.Rack rack, int x, int ty, int w, int[] col, int index, double total) {
        boolean hovered = panel.row(g, x, ty, w, index, rack.pos().equals(selected));
        panel.cell(g, Texts.pos(rack.pos()), col, 0, ty, TechPanel.TEXT_MUTED);
        panel.cell(g, Texts.productName(rack.product()).getString(), col, 1, ty, TechPanel.TEXT);
        // customers / places offered (hardware capacity × fill); the capacity itself is in the tooltip
        boolean fillable = Texts.hasFill(rack.product());
        double places = fillable ? rack.capacity() * rack.fill() / 100.0 : rack.capacity();
        panel.cell(g, GuiText.compactNumber(Math.round(rack.customers())) + " / " + GuiText.compactNumber(Math.round(places)),
                col, 2, ty, fillable && rack.fill() > 100 ? TechPanel.WARN : TechPanel.TEXT);
        if (fillable && hovered) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("data_center_addon.hosting.racks.tip.places", GuiText.compactNumber(Math.round(places)),
                    rack.fill()));
            lines.add(Component.translatable("data_center_addon.hosting.racks.tip.capacity",
                    GuiText.compactNumber(Math.round(rack.capacity()))).withStyle(ChatFormatting.GRAY));
            if (rack.fill() > 100) {
                lines.add(Component.translatable("data_center_addon.hosting.racks.tip.overbooked").withStyle(ChatFormatting.GOLD));
            }
            panel.tooltip(lines);
        }
        panel.cell(g, Texts.percent(total <= 0 ? 0 : (float) (rack.customers() / total)), col, 3, ty, TechPanel.TEXT_MUTED);
        panel.cell(g, GuiText.compactUsd(rack.centsPerDay()), col, 4, ty, TechPanel.GOOD);
        Component status = rack.licenceBound()
                ? Component.translatable("data_center_addon.hosting.status.licence")
                : Texts.statusName(rack.status());
        panel.line(g, status.getString(), col[5], ty + TEXT_DY, x + w - col[5],
                rack.status() == 0 && !rack.licenceBound() ? TechPanel.GOOD : TechPanel.WARN);
        panel.clickable(x, ty, w, ROW_H, () -> selected = rack.pos());
    }

    /** Product and fill of the selected rack. */
    private void controls(GuiGraphics g, @Nullable HostingSnapshot.Rack rack, int x, int py, int w) {
        g.fill(x - 4, py - 6, x + w + 4, py - 5, TechPanel.ACCENT);
        if (rack == null) {
            panel.line(g, Texts.hosting("racks.select"), x, py + 14, w, TechPanel.TEXT_MUTED);
            return;
        }
        BlockPos p = rack.pos();
        Component selectedLabel = Component.translatable("data_center_addon.hosting.racks.selected", p.getX(), p.getY(), p.getZ());
        g.drawString(panel.font, selectedLabel, x, py, TechPanel.TEXT, false);
        if (rack.licenceBound()) {
            int hx = x + panel.font.width(selectedLabel) + 10;
            panel.line(g, Texts.hosting("racks.licence_hint"), hx, py, x + w - hx, TechPanel.WARN);
        }
        int by = py + 16;
        String productLabel = Texts.hosting("column.product");
        int labelW = panel.font.width(productLabel);
        g.drawString(panel.font, productLabel, x, by + 4, TechPanel.TEXT_MUTED, false);
        int productW = Math.min(180, w / 2 - labelW - 16);
        panel.stepper(g, x + labelW + 8, by, productW, Texts.productName(rack.product()).getString(),
                () -> send(rack, HostingManagerService.Action.PRODUCT_PREVIOUS), () -> send(rack, HostingManagerService.Action.PRODUCT_NEXT));
        if (Texts.hasFill(rack.product())) {
            String fillLabel = Texts.hosting("racks.fill");
            int fx = x + w / 2 + 8;
            g.drawString(panel.font, fillLabel, fx, by + 4, TechPanel.TEXT_MUTED, false);
            panel.stepper(g, fx + panel.font.width(fillLabel) + 8, by, 80, rack.fill() + " %",
                    () -> send(rack, HostingManagerService.Action.FILL_DOWN), () -> send(rack, HostingManagerService.Action.FILL_UP));
        }
    }

    private void send(HostingSnapshot.Rack rack, HostingManagerService.Action action) {
        Panel.send(new HostingManagerPayloads.Change(manager.get(), rack.pos(), action));
    }
}
