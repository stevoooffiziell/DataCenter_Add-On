package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.contract.ContractService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

/** Hosting reservations: running contracts (with cancel) and offers (with accept). */
final class ReservationsView implements ManagerTab {
    private static final int BUTTON_W = 58;
    private static final int NOTE_H = 20;

    private final Panel panel;
    private final Supplier<BlockPos> manager;
    private final Confirm cancel = new Confirm();

    ReservationsView(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        List<HostingSnapshot.Contract> active = s.activeContracts();
        List<HostingSnapshot.Contract> offers = s.offers();
        if (active.isEmpty() && offers.isEmpty()) {
            panel.message(g, x, y, w, h, Component.translatable("data_center_addon.contract.none"));
            return;
        }
        int[] col = Panel.columns(x, w - BUTTON_W - 6, 0, 0.27, 0.53, 0.75, 0.88);
        int ty = y;

        // running contracts
        ty = sectionHeader(g, Component.translatable("data_center_addon.contract.active", active.size(), ContractService.MAX_ACTIVE)
                .getString(), x, ty, w);
        if (active.isEmpty()) {
            panel.line(g, Texts.contract("no_active"), x, ty + TEXT_DY, w, TechPanel.TEXT_MUTED);
            ty += ROW_H;
        }
        for (int i = 0; i < active.size(); i++) {
            HostingSnapshot.Contract c = active.get(i);
            contractRow(g, c, x, ty, w, col, i);
            panel.cell(g, c.daysDone() + " / " + Texts.days(c.days()) + "  " + strikes(c.strikes()), col, 3, ty,
                    c.strikes() > 0 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
            boolean confirming = cancel.armed(c.id());
            panel.button(g, x + w - BUTTON_W, ty + 1, BUTTON_W, Texts.contract(confirming ? "confirm" : "cancel"),
                    confirming ? TechPanel.DANGER : TechPanel.SLOT_BG,
                    () -> cancel.click(c.id(), () -> send(c, ContractService.Action.CANCEL)));
            ty += ROW_H;
        }

        // offers
        ty = sectionHeader(g, Texts.contract("offers"), x, ty + 6, w);
        boolean full = active.size() >= ContractService.MAX_ACTIVE;
        for (int i = 0; i < offers.size() && ty + ROW_H <= y + h - NOTE_H; i++) {
            HostingSnapshot.Contract c = offers.get(i);
            contractRow(g, c, x, ty, w, col, i);
            panel.cell(g, Texts.days(c.days()), col, 3, ty, TechPanel.TEXT_MUTED);
            if (full) {
                panel.button(g, x + w - BUTTON_W, ty + 1, BUTTON_W, Texts.contract("full"), Panel.BUTTON_OFF, null);
            } else {
                panel.button(g, x + w - BUTTON_W, ty + 1, BUTTON_W, Texts.contract("accept"), TechPanel.SLOT_BG,
                        () -> send(c, ContractService.Action.ACCEPT));
            }
            ty += ROW_H;
        }

        panel.wrapped(g, Component.translatable("data_center_addon.contract.note"), x, y + h - NOTE_H, w, TechPanel.TEXT_MUTED, 2);
    }

    /** A blue title with a line under it; returns the y of the first row. */
    private int sectionHeader(GuiGraphics g, String title, int x, int y, int w) {
        panel.line(g, title, x, y + TEXT_DY, w, TechPanel.ACCENT);
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, Panel.HEADER_LINE);
        return y + ROW_H + 2;
    }

    /** Columns shared by running contracts and offers: client, places, coverage, pay; and the tooltip. */
    private void contractRow(GuiGraphics g, HostingSnapshot.Contract c, int x, int ty, int w, int[] col, int index) {
        if (panel.hover(x, ty, w - BUTTON_W - 6, ROW_H)) {
            g.fill(x - 4, ty, x + w + 4, ty + ROW_H, Panel.ROW_HOVER);
            panel.tooltip(tooltip(c));
        } else {
            Panel.stripe(g, x, ty, w, index);
        }
        panel.cell(g, label(c), col, 0, ty, c.urgent() ? TechPanel.WARN : TechPanel.TEXT);
        panel.cell(g, c.accounts() + "× " + Texts.productName(c.product()).getString(), col, 1, ty, TechPanel.TEXT);
        coverage(g, c, col[2], ty, col[3] - col[2] - 8);
        panel.cell(g, GuiText.compactUsd(c.centsPerDay()), col, 4, ty, TechPanel.GOOD);
    }

    /** "free / needed" with a bar: green when the free places cover the contracts, amber when they do not. */
    private void coverage(GuiGraphics g, HostingSnapshot.Contract c, int x, int y, int w) {
        float ratio = c.required() <= 0 ? 1 : (float) Math.min(1.0, c.free() / c.required());
        int barW = Math.max(16, w - 52);
        TechPanel.bar(g, x, y + 3, barW, 9, ratio, c.covered() ? TechPanel.GOOD : TechPanel.WARN);
        String label = GuiText.compactNumber((long) Math.floor(c.free())) + "/" + c.required();
        panel.line(g, label, x + barW + 4, y + TEXT_DY, w - barW - 4, c.covered() ? TechPanel.TEXT_MUTED : TechPanel.WARN);
    }

    /** Client name with a mark for emergency and mini orders. */
    private static String label(HostingSnapshot.Contract c) {
        String name = ContractService.clientName(c.client()).getString();
        return c.urgent() ? "⚡ " + name : c.mini() ? Texts.contract("mini") + " · " + name : name;
    }

    private static List<Component> tooltip(HostingSnapshot.Contract c) {
        List<Component> lines = new ArrayList<>();
        lines.add(ContractService.clientName(c.client()).copy().withStyle(ChatFormatting.AQUA));
        if (c.urgent()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.urgent").withStyle(ChatFormatting.GOLD));
        } else if (c.mini()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.mini").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("data_center_addon.contract.tip.wants", c.accounts(), Texts.productName(c.product()), c.days()));
        lines.add(Component.translatable("data_center_addon.contract.tip.free", GuiText.compactNumber((long) Math.floor(c.free())),
                c.required()).withStyle(c.covered() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        lines.add(Component.translatable("data_center_addon.contract.tip.pay", GuiText.compactUsd(c.centsPerDay()),
                GuiText.compactUsd(c.bonusCents()), c.reputation()).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("data_center_addon.contract.tip.penalty", GuiText.compactUsd(c.penaltyCents()),
                c.reputationLoss(), ContractService.MAX_STRIKES).withStyle(ChatFormatting.RED));
        if (c.offer()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.expires", c.expiresIn()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("data_center_addon.contract.tip.today", Math.round(c.metToday() * 100),
                    ContractService.DAY_SHARE_PERCENT).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** ●●○ */
    private static String strikes(int strikes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ContractService.MAX_STRIKES; i++) {
            sb.append(i < strikes ? '●' : '○');
        }
        return sb.toString();
    }

    private void send(HostingSnapshot.Contract c, ContractService.Action action) {
        Panel.send(new HostingManagerPayloads.ContractChange(manager.get(), c.id(), action));
    }
}
