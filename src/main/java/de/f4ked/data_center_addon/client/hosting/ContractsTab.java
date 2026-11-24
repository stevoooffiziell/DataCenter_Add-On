package de.f4ked.data_center_addon.client.hosting;

import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;

/** Contracts: a switch between hosting reservations and delivery orders (the latter open first). */
final class ContractsTab implements ManagerTab {
    private final Panel panel;
    private final ReservationsView reservations;
    private final DeliveriesView deliveries;
    private boolean showDeliveries = true;

    ContractsTab(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.reservations = new ReservationsView(panel, manager);
        this.deliveries = new DeliveriesView(panel, manager);
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        String reservationsLabel = Component.translatable("data_center_addon.delivery.view.reservations", s.activeContracts().size())
                .getString();
        String deliveriesLabel = Component.translatable("data_center_addon.delivery.view.deliveries", s.runningDeliveries().size())
                .getString();
        int bw = Math.max(panel.font.width(reservationsLabel), panel.font.width(deliveriesLabel)) + 14;
        panel.toggle(g, x, y, bw, reservationsLabel, !showDeliveries, () -> showDeliveries = false);
        panel.toggle(g, x + bw + 4, y, bw, deliveriesLabel, showDeliveries, () -> showDeliveries = true);
        int top = ROW_H + 4;
        (showDeliveries ? deliveries : reservations).render(g, s, x, y + top, w, h - top);
    }
}
