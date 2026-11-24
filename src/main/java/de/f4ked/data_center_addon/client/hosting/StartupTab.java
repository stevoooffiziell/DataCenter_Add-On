package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.startup.Milestone;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import static de.f4ked.data_center_addon.client.hosting.Panel.LINE_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.ROW_H;
import static de.f4ked.data_center_addon.client.hosting.Panel.TEXT_DY;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Startup: start bonus, grant, setup fees, prepayment and founding loan on the left, milestones on the
 * right, service tickets at the bottom.
 */
final class StartupTab implements ManagerTab {
    private static final int LOAN_BUTTON_W = 64;
    private static final int TOGGLE_W = 34;
    private static final int MILESTONE_H = 10;

    private final Panel panel;
    private final Supplier<BlockPos> manager;
    private final Confirm takeLoan = new Confirm();

    StartupTab(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        HostingSnapshot.Startup st = s.startup();
        int ticketsH = ROW_H + 2 + Math.max(1, st.tickets().size()) * ROW_H;
        int topH = h - ticketsH - 8;
        int leftW = (int) (w * 0.56);
        int rightX = x + leftW + 10;

        int ty = income(g, st, x, y, leftW);
        loan(g, st, x, ty + 6, leftW);
        g.fill(rightX - 6, y, rightX - 5, y + topH, Panel.HEADER_LINE);
        milestones(g, st, rightX, y, w - leftW - 10, topH);
        tickets(g, st, x, y + h - ticketsH, w);
    }

    /** Start bonus, grant, setup fees and prepayment; returns the y below them. */
    private int income(GuiGraphics g, HostingSnapshot.Startup st, int x, int ty, int w) {
        panel.sectionTitle(g, x, ty, w, Texts.startup("bonus"));
        ty += 13;
        Component bonus;
        int bonusColor = TechPanel.TEXT_MUTED;
        if (st.veteran()) {
            bonus = Component.translatable("data_center_addon.startup.bonus.veteran");
        } else if (!st.started()) {
            bonus = Component.translatable("data_center_addon.startup.bonus.waiting", Texts.bonus(st.bonusFactor()));
            bonusColor = TechPanel.TEXT;
        } else if (st.bonusFactor() > 0) {
            bonus = st.bonusMode() == 0
                    ? Component.translatable("data_center_addon.startup.bonus.time", Texts.bonus(st.bonusFactor()), st.bonusDaysLeft())
                    : Component.translatable("data_center_addon.startup.bonus.revenue", Texts.bonus(st.bonusFactor()),
                    GuiText.compactUsd(st.bonusRevenueLeft()));
            bonusColor = TechPanel.GOOD;
        } else {
            bonus = Component.translatable("data_center_addon.startup.bonus.ended");
        }
        ty = panel.wrapped(g, bonus, x, ty, w, bonusColor, 2);
        if (st.started() && !st.veteran()) {
            panel.line(g, Component.translatable("data_center_addon.startup.bonus.paid", GuiText.compactUsd(st.bonusPaidCents())).getString(),
                    x, ty, w, TechPanel.TEXT_MUTED);
            ty += LINE_H;
        }
        ty += 2;

        String grant = st.grantCents() > 0
                ? Component.translatable("data_center_addon.startup.grant.paid", GuiText.compactUsd(st.grantCents())).getString()
                : st.grantOfferCents() > 0 && !st.started()
                ? Component.translatable("data_center_addon.startup.grant.waiting", GuiText.compactUsd(st.grantOfferCents())).getString()
                : Texts.startup("grant.none");
        panel.line(g, grant, x, ty, w, st.grantCents() > 0 ? TechPanel.GOOD : TechPanel.TEXT_MUTED);
        ty += LINE_H;
        panel.line(g, Component.translatable("data_center_addon.startup.fees", GuiText.compactUsd(st.feesPaidCents())).getString(),
                x, ty, w, TechPanel.TEXT_MUTED);
        ty += LINE_H;
        if (st.prepay().available()) {
            prepay(g, st.prepay(), x, ty, w);
            ty += ROW_H;
        }
        return ty;
    }

    /** "Prepayment: $X received · $Y still to settle" with an on/off button. */
    private void prepay(GuiGraphics g, HostingSnapshot.Prepay prepay, int x, int ty, int w) {
        int textW = w - TOGGLE_W - 6;
        String state = prepay.enabled()
                ? Component.translatable("data_center_addon.startup.prepay.state", GuiText.compactUsd(prepay.receivedCents()),
                GuiText.compactUsd(prepay.openCents())).getString()
                : Texts.startup("prepay.off");
        panel.line(g, state, x, ty + 2, textW, TechPanel.TEXT_MUTED);
        panel.button(g, x + w - TOGGLE_W, ty, TOGGLE_W, Texts.startup(prepay.enabled() ? "prepay.on_label" : "prepay.off_label"),
                prepay.enabled() ? Panel.BUTTON_PRIMARY : TechPanel.SLOT_BG,
                () -> send(prepay.enabled() ? StartupService.Action.PREPAY_OFF : StartupService.Action.PREPAY_ON));
        if (panel.hover(x, ty, textW, ROW_H - 2)) {
            panel.tooltip(List.of(Component.translatable("data_center_addon.startup.prepay"),
                    Component.translatable("data_center_addon.startup.prepay.hint", prepay.sharePercent(), prepay.discountPercent())
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("data_center_addon.startup.prepay.customers", prepay.customers()).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** The founding loan: offer with "take" (two clicks), or what is still owed with "repay". */
    private void loan(GuiGraphics g, HostingSnapshot.Startup st, int x, int ty, int w) {
        panel.sectionTitle(g, x, ty, w, Texts.startup("loan"));
        ty += 13;
        int textW = w - LOAN_BUTTON_W - 6;
        int buttonX = x + w - LOAN_BUTTON_W;
        if (st.loanOfferCents() <= 0) {
            panel.line(g, Texts.startup("loan.none"), x, ty, w, TechPanel.TEXT_MUTED);
        } else if (!st.loanTaken()) {
            panel.wrapped(g, Component.translatable("data_center_addon.startup.loan.offer", GuiText.compactUsd(st.loanOfferCents()),
                    st.loanGraceDays(), GuiText.compactUsd(st.loanInstalmentCents())), x, ty, textW, TechPanel.TEXT, 2);
            boolean confirming = takeLoan.armed(StartupService.Action.TAKE_LOAN);
            panel.button(g, buttonX, ty, LOAN_BUTTON_W, Texts.startup(confirming ? "loan.confirm" : "loan.take"),
                    confirming ? TechPanel.WARN : Panel.BUTTON_PRIMARY,
                    () -> takeLoan.click(StartupService.Action.TAKE_LOAN, () -> send(StartupService.Action.TAKE_LOAN)));
        } else if (st.loanOutstandingCents() > 0) {
            panel.line(g, Component.translatable("data_center_addon.startup.loan.outstanding",
                    GuiText.compactUsd(st.loanOutstandingCents())).getString(), x, ty, textW, TechPanel.WARN);
            Component terms = st.loanGraceDays() > 0
                    ? Component.translatable("data_center_addon.startup.loan.grace", st.loanGraceDays(),
                    GuiText.compactUsd(st.loanInstalmentCents()))
                    : Component.translatable("data_center_addon.startup.loan.instalment", GuiText.compactUsd(st.loanInstalmentCents()));
            panel.line(g, terms.getString(), x, ty + LINE_H, textW, TechPanel.TEXT_MUTED);
            panel.button(g, buttonX, ty, LOAN_BUTTON_W, Texts.startup("loan.repay"), TechPanel.SLOT_BG,
                    () -> send(StartupService.Action.REPAY_LOAN));
        } else {
            panel.line(g, Texts.startup("loan.repaid"), x, ty, w, TechPanel.GOOD);
        }
    }

    /** Every milestone with ✔ / ○ and its reward. */
    private void milestones(GuiGraphics g, HostingSnapshot.Startup st, int x, int y, int w, int h) {
        Milestone[] all = Milestone.values();
        long reached = Arrays.stream(all).filter(st::reached).count();
        panel.sectionTitle(g, x, y, w, Component.translatable("data_center_addon.startup.milestones", reached, all.length).getString());
        int my = y + 13;
        for (Milestone m : all) {
            if (my + MILESTONE_H > y + h) {
                break;
            }
            boolean done = st.reached(m);
            long reward = m.ordinal() < st.milestoneRewards().size() ? st.milestoneRewards().get(m.ordinal()) : 0;
            String rewardText = reward > 0 ? GuiText.compactUsd(reward) : "";
            int rewardW = panel.font.width(rewardText);
            g.drawString(panel.font, done ? "✔" : "○", x, my, done ? TechPanel.GOOD : TechPanel.TEXT_MUTED, false);
            panel.line(g, Component.translatable(m.translationKey()).getString(), x + 10, my, w - rewardW - 16,
                    done ? TechPanel.TEXT_MUTED : TechPanel.TEXT);
            g.drawString(panel.font, rewardText, x + w - rewardW, my, done ? TechPanel.TEXT_MUTED : TechPanel.GOOD, false);
            my += MILESTONE_H;
        }
    }

    /** The open service tickets: what, which rack, reward, deadline. */
    private void tickets(GuiGraphics g, HostingSnapshot.Startup st, int x, int by, int w) {
        g.fill(x - 4, by - 5, x + w + 4, by - 4, TechPanel.ACCENT);
        int[] col = Panel.columns(x, w, 0, 0.30, 0.62, 0.84);
        panel.header(g, x, by, w, col, "column.ticket", "column.rack", "column.reward", "column.due");
        int ry = by + ROW_H + 2;
        if (st.tickets().isEmpty()) {
            panel.line(g, Texts.startup("tickets.none"), x, ry + TEXT_DY, w, TechPanel.TEXT_MUTED);
        }
        for (int i = 0; i < st.tickets().size(); i++) {
            HostingSnapshot.Ticket t = st.tickets().get(i);
            Component kind = Component.translatable("data_center_addon.startup.ticket.kind." + t.kind());
            Panel.stripe(g, x, ry, w, i);
            panel.cell(g, kind.getString(), col, 0, ry, TechPanel.TEXT);
            panel.cell(g, Texts.pos(t.pos()), col, 1, ry, TechPanel.TEXT_MUTED);
            panel.cell(g, GuiText.compactUsd(t.rewardCents()), col, 2, ry, TechPanel.GOOD);
            panel.cell(g, Texts.days(t.daysLeft()), col, 3, ry, t.daysLeft() <= 1 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
            if (panel.hover(x, ry, w, ROW_H)) {
                panel.tooltip(List.of(kind, Component.translatable("data_center_addon.startup.tickets.hint").withStyle(ChatFormatting.GRAY)));
            }
            ry += ROW_H;
        }
    }

    private void send(StartupService.Action action) {
        Panel.send(new HostingManagerPayloads.StartupChange(manager.get(), action));
    }
}
