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
import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
        List<HostingSnapshot.Rack> racks = s.racks();
        Map<BlockPos, double[]> contracts = contractShares(s);
        double total = s.totalCustomers();
        for (double[] share : contracts.values()) {
            total += share[0];
        }
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - CONTROLS_H) / ROW_H;
        panel.clampScroll(racks.size(), visible);
        HostingSnapshot.Rack current = null;
        for (HostingSnapshot.Rack rack : racks) {
            if (rack.pos().equals(selected)) {
                current = rack;
            }
        }
        int ty = top;
        for (int i = panel.scroll; i < racks.size() && i < panel.scroll + visible; i++) {
            HostingSnapshot.Rack rack = racks.get(i);
            rackRow(g, rack, contracts.getOrDefault(rack.pos(), NO_CONTRACTS), x, ty, w, col, i, total);
            ty += ROW_H;
        }
        panel.scrollbar(g, x + w + 3, top, visible * ROW_H, racks.size(), visible);
        controls(g, current, x, y + h - CONTROLS_H + 8, w);
    }

    /** No contract places on a rack: {places, cents per day}. */
    private static final double[] NO_CONTRACTS = {0, 0};

    /**
     * The places running contracts reserve, and what they pay, spread over the racks of their product in
     * proportion to the places each offers (evenly if none offers any): rack → {places, cents per day}.
     * Contracts of a product without a rack on this network are left out.
     */
    private static Map<BlockPos, double[]> contractShares(HostingSnapshot s) {
        Map<Integer, double[]> byProduct = new HashMap<>();
        for (HostingSnapshot.Contract c : s.activeContracts()) {
            double[] sum = byProduct.computeIfAbsent(c.product(), p -> new double[2]);
            sum[0] += c.accounts();
            sum[1] += c.centsPerDay();
        }
        Map<BlockPos, double[]> shares = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : byProduct.entrySet()) {
            List<HostingSnapshot.Rack> racks = s.racks().stream().filter(r -> r.product() == entry.getKey()).toList();
            double offered = racks.stream().mapToDouble(RacksTab::placesOffered).sum();
            for (HostingSnapshot.Rack rack : racks) {
                double part = offered > 0 ? placesOffered(rack) / offered : 1.0 / racks.size();
                shares.put(rack.pos(), new double[]{entry.getValue()[0] * part, entry.getValue()[1] * part});
            }
        }
        return shares;
    }

    /** Places a rack offers: hardware capacity × fill (shared hosting and VPS), else its capacity. */
    private static double placesOffered(HostingSnapshot.Rack rack) {
        return Texts.hasFill(rack.product()) ? rack.capacity() * rack.fill() / 100.0 : rack.capacity();
    }

    /** {@code contracts}: this rack's share of the contract places and pay, counted with its customers and income. */
    private void rackRow(GuiGraphics g, HostingSnapshot.Rack rack, double[] contracts, int x, int ty, int w, int[] col, int index,
                         double total) {
        boolean hovered = panel.row(g, x, ty, w, index, rack.pos().equals(selected));
        panel.cell(g, Texts.pos(rack.pos()), col, 0, ty, TechPanel.TEXT_MUTED);
        panel.cell(g, Texts.productName(rack.product()).getString(), col, 1, ty, TechPanel.TEXT);
        // customers (with contract places) / places offered (hardware capacity × fill); details in the tooltip
        boolean fillable = Texts.hasFill(rack.product());
        double places = placesOffered(rack);
        double customers = rack.customers() + contracts[0];
        panel.cell(g, GuiText.compactNumber(Math.round(customers)) + " / " + GuiText.compactNumber(Math.round(places)),
                col, 2, ty, fillable && rack.fill() > 100 ? TechPanel.WARN : TechPanel.TEXT);
        if (hovered && (fillable || contracts[0] > 0)) {
            List<Component> lines = new ArrayList<>();
            if (contracts[0] > 0) {
                lines.add(Component.translatable("data_center_addon.hosting.racks.tip.customers",
                        GuiText.compactNumber(Math.round(rack.customers())), GuiText.compactNumber(Math.round(contracts[0])),
                        GuiText.compactUsd(Math.round(contracts[1]))));
            }
            if (fillable) {
                lines.add(Component.translatable("data_center_addon.hosting.racks.tip.places", GuiText.compactNumber(Math.round(places)),
                        rack.fill()));
                lines.add(Component.translatable("data_center_addon.hosting.racks.tip.capacity",
                        GuiText.compactNumber(Math.round(rack.capacity()))).withStyle(ChatFormatting.GRAY));
                if (rack.fill() > 100) {
                    lines.add(Component.translatable("data_center_addon.hosting.racks.tip.overbooked").withStyle(ChatFormatting.GOLD));
                }
            }
            panel.tooltip(lines);
        }
        panel.cell(g, Texts.percent(total <= 0 ? 0 : (float) (customers / total)), col, 3, ty, TechPanel.TEXT_MUTED);
        panel.cell(g, GuiText.compactUsd(rack.centsPerDay() + Math.round(contracts[1])), col, 4, ty, TechPanel.GOOD);
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
