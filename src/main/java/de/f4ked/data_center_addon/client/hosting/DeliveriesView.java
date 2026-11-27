package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.contract.ContractRarity;
import de.f4ked.data_center_addon.hosting.contract.ContractService;
import de.f4ked.data_center_addon.hosting.contract.DeliveryService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.LINE_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/**
 * Delivery orders: running ones and offers on the left, the selected one in detail on the right. Every order
 * shows its rarity in the colours of the computer parts' tiers.
 */
final class DeliveriesView implements ManagerTab {
    private final Panel panel;
    private final Supplier<BlockPos> manager;
    private final Confirm cancel = new Confirm();
    private int selected = -1;

    DeliveriesView(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        List<HostingSnapshot.Delivery> running = s.runningDeliveries();
        List<HostingSnapshot.Delivery> offers = s.deliveryOffers();
        if (running.isEmpty() && offers.isEmpty()) {
            panel.message(g, x, y, w, h, Component.translatable("data_center_addon.delivery.none"));
            return;
        }
        HostingSnapshot.Delivery current = s.deliveries().stream().filter(d -> d.id() == selected).findFirst()
                .orElse(running.isEmpty() ? offers.get(0) : running.get(0));
        selected = current.id();

        int detailW = Math.max(150, (int) (w * 0.42));
        int listW = w - detailW - 8;
        int[] col = Panel.columns(x, listW, 0, 0.50, 0.68, 0.82);
        int maxActive = DeliveryService.maxActive(s.customerClass());
        if (panel.hover(x, y, listW, ROW_H)) {
            panel.tooltip(ReservationsView.headerTooltip(s.customerClass()));
        }
        int ty = sectionHeader(g, Component.translatable("data_center_addon.delivery.running", running.size(), maxActive)
                .getString(), x, y, listW);
        if (running.isEmpty()) {
            panel.line(g, Texts.delivery("no_running"), x, ty + TEXT_DY, listW, TechPanel.TEXT_MUTED);
            ty += ROW_H;
        }
        for (int i = 0; i < running.size(); i++) {
            row(g, running.get(i), x, ty, listW, col, i);
            ty += ROW_H;
        }
        ty = sectionHeader(g, Texts.delivery("offers"), x, ty + 6, listW);
        for (int i = 0; i < offers.size() && ty + ROW_H <= y + h - 10; i++) {
            row(g, offers.get(i), x, ty, listW, col, i);
            ty += ROW_H;
        }
        panel.line(g, Texts.delivery("note"), x, y + h - 9, listW, TechPanel.TEXT_MUTED);

        detail(g, current, running.size() >= maxActive, x + listW + 8, y, detailW, h);
    }

    /** A blue title with a line under it; returns the y of the first row. */
    private int sectionHeader(GuiGraphics g, String title, int x, int y, int w) {
        panel.line(g, title, x, y + TEXT_DY, w, TechPanel.ACCENT);
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, Panel.HEADER_LINE);
        return y + ROW_H + 2;
    }

    private void row(GuiGraphics g, HostingSnapshot.Delivery d, int x, int y, int w, int[] col, int index) {
        panel.row(g, x, y, w, index, d.id() == selected);
        // a bar in the rarity's colour at the left edge, the client's name in it
        int color = rarityColor(d);
        g.fill(x - 4, y + 1, x - 2, y + ROW_H - 1, color);
        panel.cell(g, ContractService.clientName(d.client()).getString(), col, 0, y, color);
        panel.cell(g, d.offer() ? "—" : d.delivered() + "/" + d.total(), col, 1, y,
                !d.offer() && d.canDeliver() ? TechPanel.GOOD : TechPanel.TEXT_MUTED);
        panel.cell(g, Texts.days(d.daysLeft()), col, 2, y, d.daysLeft() <= 1 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
        int payW = x + w - col[3];
        String pay = GuiText.compactUsd(d.payCents());
        panel.line(g, pay, x + w - Math.min(payW, panel.font.width(pay)), y + TEXT_DY, payW, TechPanel.GOOD);
        panel.clickable(x, y, w, ROW_H, () -> selected = d.id());
    }

    /** The selected delivery: client, deadline, goods with icons and progress, pay, buttons. */
    private void detail(GuiGraphics g, HostingSnapshot.Delivery d, boolean full, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x60000000);
        g.fill(x, y, x + 1, y + h, Panel.HEADER_LINE);
        int px = x + 6, pw = w - 12, ty = y + 4;
        panel.line(g, ContractService.clientName(d.client()).getString(), px, ty, pw, rarityColor(d));
        ty += LINE_H;
        panel.line(g, Component.translatable("data_center_addon.contract.tip.rarity",
                Component.translatable(ContractRarity.of(d.rarity()).translationKey())).getString(), px, ty, pw, rarityColor(d));
        ty += LINE_H;
        String status = d.offer()
                ? Component.translatable("data_center_addon.delivery.detail.offer", d.daysLeft(), d.days()).getString()
                : Component.translatable("data_center_addon.delivery.detail.running", d.daysLeft()).getString();
        panel.line(g, status, px, ty, pw, d.daysLeft() <= 1 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
        ty += 14;

        for (HostingSnapshot.Goal goal : d.goals()) {
            goal(g, d, goal, px, ty, pw);
            ty += 22;
        }

        ty += 2;
        panel.line(g, Component.translatable("data_center_addon.delivery.detail.pay", GuiText.compactUsd(d.payCents()), d.reputation())
                .getString(), px, ty, pw, TechPanel.GOOD);
        ty += LINE_H;
        panel.line(g, Component.translatable("data_center_addon.delivery.detail.fail", d.reputation()).getString(), px, ty, pw,
                TechPanel.DANGER);

        int by = y + h - ROW_H - 3;
        int half = (pw - 4) / 2;
        if (d.offer()) {
            if (full) {
                panel.button(g, px, by, half, Texts.contract("full"), Panel.BUTTON_OFF, null);
            } else {
                panel.button(g, px, by, half, Texts.contract("accept"), Panel.BUTTON_PRIMARY, () -> send(d, DeliveryService.Action.ACCEPT));
            }
            panel.button(g, px + half + 4, by, half, Texts.delivery("decline"), TechPanel.SLOT_BG,
                    () -> send(d, DeliveryService.Action.DECLINE));
        } else {
            panel.button(g, px, by, half, Texts.delivery("deliver"), d.canDeliver() ? Panel.BUTTON_PRIMARY : TechPanel.SLOT_BG,
                    d.canDeliver() ? () -> send(d, DeliveryService.Action.DELIVER) : null);
            boolean confirming = cancel.armed(d.id());
            panel.button(g, px + half + 4, by, half, Texts.contract(confirming ? "confirm" : "cancel"),
                    confirming ? TechPanel.DANGER : TechPanel.SLOT_BG,
                    () -> cancel.click(d.id(), () -> send(d, DeliveryService.Action.CANCEL)));
        }
    }

    /** One goal: item icon, "n× name" and progress (or how many the player carries, for an offer). */
    private void goal(GuiGraphics g, HostingSnapshot.Delivery d, HostingSnapshot.Goal goal, int px, int ty, int pw) {
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(goal.item()));
        g.fill(px, ty, px + 18, ty + 18, TechPanel.SLOT_BG);
        g.renderItem(icon, px + 1, ty + 1);
        panel.line(g, goal.count() + "× " + goalName(goal, icon).getString(), px + 22, ty, pw - 24, TechPanel.TEXT);
        boolean done = goal.delivered() >= goal.count();
        String progress = d.offer()
                ? Component.translatable("data_center_addon.delivery.detail.have", goal.inInventory()).getString()
                : goal.delivered() + " / " + goal.count()
                + (done ? " ✔" : goal.inInventory() > 0 ? "  (+" + Math.min(goal.inInventory(), goal.count() - goal.delivered()) + ")" : "");
        panel.line(g, progress, px + 22, ty + 10, pw - 24,
                done ? TechPanel.GOOD : goal.inInventory() > 0 ? TechPanel.ACCENT : TechPanel.TEXT_MUTED);
        if (panel.hover(px, ty, 18, 18) && !icon.isEmpty()) {
            panel.tooltip(List.of(goalName(goal, icon), Component.translatable("data_center_addon.delivery.goal.hint."
                    + goalKind(goal).name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static int rarityColor(HostingSnapshot.Delivery d) {
        return 0xFF000000 | ContractService.color(ContractRarity.of(d.rarity()));
    }

    private static DeliveryService.GoalKind goalKind(HostingSnapshot.Goal goal) {
        DeliveryService.GoalKind[] kinds = DeliveryService.GoalKind.values();
        return goal.kind() >= 0 && goal.kind() < kinds.length ? kinds[goal.kind()] : DeliveryService.GoalKind.ITEM;
    }

    private static Component goalName(HostingSnapshot.Goal goal, ItemStack icon) {
        DeliveryService.GoalKind kind = goalKind(goal);
        return kind == DeliveryService.GoalKind.ITEM ? icon.getHoverName()
                : Component.translatable("data_center_addon.delivery.goal." + kind.name().toLowerCase(Locale.ROOT));
    }

    private void send(HostingSnapshot.Delivery d, DeliveryService.Action action) {
        Panel.send(new HostingManagerPayloads.DeliveryChange(manager.get(), d.id(), action));
    }
}
