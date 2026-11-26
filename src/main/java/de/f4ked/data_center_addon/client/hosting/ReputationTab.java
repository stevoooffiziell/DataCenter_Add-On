package de.f4ked.data_center_addon.client.hosting;

import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.reputation.Campaigns;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.Renown;
import de.f4ked.data_center_addon.hosting.reputation.Reviews;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.client.hosting.Panel.LINE_H;

/**
 * Reputation: the account's Ruf (quality of the last 30 days) on the left, its Bekanntheit (renown) and
 * where it came from on the right.
 */
final class ReputationTab implements ManagerTab {
    private final Panel panel;
    private final Supplier<BlockPos> manager;
    private final Confirm book = new Confirm();

    ReputationTab(Panel panel, Supplier<BlockPos> manager) {
        this.panel = panel;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int leftW = (w - 12) / 2;
        int classH = s.standing().campaigns().isEmpty() ? 36 : 52;
        ruf(g, s.standing(), x, y, leftW);
        g.fill(x + leftW + 5, y, x + leftW + 6, y + h - classH - 4, Panel.HEADER_LINE);
        renown(g, s.standing(), x + leftW + 12, y, w - leftW - 12);
        classes(g, s, x, y + h - classH, w);
        if (panel.hover(x, y, w, 12)) {
            panel.tooltip(List.of(Texts.standingSummary(s.standing(), 10)));
        }
        campaigns(g, s.standing(), x, y + h - 14, w);
    }

    /** "Advertising: [Magazine $1.5K +10] [Fair $5K +35] ..."; two clicks book one. */
    private void campaigns(GuiGraphics g, HostingSnapshot.Standing st, int x, int y, int w) {
        if (st.campaigns().isEmpty()) {
            return;
        }
        String label = Texts.reputation("campaigns") + " ";
        g.drawString(panel.font, label, x, y + 3, TechPanel.TEXT_MUTED, false);
        int bx = x + panel.font.width(label);
        int n = st.campaigns().size();
        int bw = Math.max(40, (x + w - bx) / n - 3);
        Campaigns.Campaign[] kinds = Campaigns.Campaign.values();
        for (HostingSnapshot.Campaign c : st.campaigns()) {
            Campaigns.Campaign kind = kinds[Math.min(c.kind(), kinds.length - 1)];
            String name = Component.translatable(kind.key()).getString();
            boolean ready = c.daysLeft() <= 0;
            boolean armed = book.armed(c.kind());
            String text = armed ? Texts.reputation("campaign.confirm") : ready ? name + " " + GuiText.compactUsd(c.costCents())
                    : name + " " + Texts.days(c.daysLeft());
            panel.button(g, bx, y, bw, text, armed ? Panel.BUTTON_PRIMARY : TechPanel.SLOT_BG, !ready ? null : () ->
                    book.click(c.kind(), () -> Panel.send(new HostingManagerPayloads.CampaignBooking(manager.get(), c.kind()))));
            if (panel.hover(bx, y, bw, Panel.ROW_H)) {
                panel.tooltip(List.of(Component.translatable(kind.key()),
                        Component.translatable("data_center_addon.reputation.campaign.tip", GuiText.compactUsd(c.costCents()),
                                "+" + c.renown()).withStyle(ChatFormatting.GRAY)));
            }
            bx += bw + 3;
        }
    }

    /** The customer class and what the next one needs: Ruf, renown and the room's tier. */
    private void classes(GuiGraphics g, HostingSnapshot s, int x, int y, int w) {
        CustomerTier current = CustomerTier.of(s.customerClass());
        panel.sectionTitle(g, x, y, w, Component.translatable("data_center_addon.reputation.class",
                Component.translatable(current.translationKey())).getString()
                + "  (" + (current.ordinal() + 1) + "/" + CustomerTier.values().length + ")");
        int ty = y + 15;
        CustomerTier next = ClassRules.next(current);
        if (next == null) {
            panel.line(g, Texts.reputation("class.top"), x, ty, w, TechPanel.GOOD);
            return;
        }
        HostingSnapshot.Standing st = s.standing();
        double needRuf = next.ruf, needRenown = next.renown;
        boolean rufOk = st.ruf() >= needRuf - 1e-9, renownOk = st.renown() >= needRenown;
        int tx = x;
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.next",
                Component.translatable(next.translationKey())).getString() + "  ", TechPanel.TEXT);
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.ruf", Texts.percent((float) st.ruf()),
                Texts.percent((float) needRuf)).getString() + (rufOk ? " ✔" : " ✘") + "  ", rufOk ? TechPanel.GOOD : TechPanel.WARN);
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.renown", Math.round(st.renown()),
                Math.round(needRenown)).getString() + (renownOk ? " ✔" : " ✘") + "  ", renownOk ? TechPanel.GOOD : TechPanel.WARN);
        boolean roomOk = st.roomTier() >= next.room.level;
        part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.tier",
                Component.translatable(next.room.translationKey())).getString() + (roomOk ? " ✔" : " ✘"),
                roomOk ? TechPanel.GOOD : TechPanel.WARN);
        if (panel.hover(x, y, w, 26)) {
            panel.tooltip(classTip(current));
        }
    }

    /** Every class with what it needs and pays; the account's class marked. */
    private static List<Component> classTip(CustomerTier current) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("data_center_addon.reputation.class.tip").withStyle(ChatFormatting.GRAY));
        for (CustomerTier t : CustomerTier.values()) {
            lines.add(Component.translatable("data_center_addon.reputation.class.row", Component.translatable(t.translationKey()),
                    Texts.percent((float) t.ruf), Math.round(t.renown), Component.translatable(t.room.translationKey()),
                    Math.round((t.income - 1) * 100)).withStyle(t == current ? ChatFormatting.GREEN
                    : t.ordinal() < current.ordinal() ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Draws one piece of a line; returns the x after it. */
    private int part(GuiGraphics g, int x, int y, String text, int color) {
        g.drawString(panel.font, text, x, y, color, false);
        return x + panel.font.width(text);
    }

    /** Bekanntheit: bar and the renown booked per source. */
    private void renown(GuiGraphics g, HostingSnapshot.Standing st, int x, int y, int w) {
        panel.sectionTitle(g, x, y, w, Texts.reputation("renown"));
        int ty = y + 15;
        TechPanel.bar(g, x, ty, w - 44, 10, (float) (st.renown() / ClassRules.maxRenown()), TechPanel.ACCENT);
        panel.line(g, Long.toString(Math.round(st.renown())), x + w - 40, ty + 1, 40, TechPanel.TEXT);
        ty += 16;
        Renown.Source[] sources = Renown.Source.values();
        for (int i = 0; i < sources.length && i < st.sources().size(); i++) {
            double v = st.sources().get(i);
            if (Math.abs(v) < 0.5) {
                continue;
            }
            line(g, x, ty, w, Component.translatable(sources[i].key()).getString(),
                    (v > 0 ? "+" : "") + Math.round(v), v > 0 ? TechPanel.GOOD : TechPanel.DANGER);
            ty += LINE_H;
        }
        if (st.quietDays() > 20) {
            boolean forgetting = st.quietDays() > 30;
            line(g, x, ty, w, Component.translatable(forgetting ? "data_center_addon.reputation.forgetting"
                    : "data_center_addon.reputation.quiet", st.quietDays()).getString(), forgetting ? "−1 %/" + Texts.reputation("month") : "",
                    TechPanel.WARN);
            ty += LINE_H;
        }
        if (st.pending() >= 1) {
            line(g, x, ty, w, Component.translatable("data_center_addon.reputation.pending",
                    Math.round(st.pending())).getString(), "+" + Math.round(st.pending() * 0.1), TechPanel.TEXT_MUTED);
            ty += LINE_H;
        }
        ty += 4;
        if (st.review().size() == 6) {
            List<Long> r = st.review();
            boolean positive = r.get(1) == 1;
            CustomerSegment[] segments = CustomerSegment.values();
            HostingProduct[] products = HostingProduct.values();
            CustomerSegment segment = segments[(int) Math.min(r.get(3), segments.length - 1)];
            HostingProduct product = products[(int) Math.min(r.get(2), products.length - 1)];
            long ago = Math.max(0, r.get(5) - r.get(0));
            String label = Component.translatable("data_center_addon.reputation.last_review", positive ? "★★★★★" : "★☆☆☆☆",
                    Component.translatable(segment.translationKey()), ago).getString();
            panel.line(g, label, x, ty, w, positive ? TechPanel.GOOD : TechPanel.WARN);
            if (panel.hover(x, ty - 1, w, LINE_H)) {
                panel.tooltip(List.of(Reviews.message(positive, product, segment, (int) (long) r.get(4))));
            }
            ty += LINE_H + 2;
        }
        panel.wrapped(g, Component.translatable("data_center_addon.reputation.renown.note"), x, ty, w, TechPanel.TEXT_MUTED, 4);
    }

    /** The Ruf: bar, the figures of the window and what it does. */
    private void ruf(GuiGraphics g, HostingSnapshot.Standing st, int x, int y, int w) {
        panel.sectionTitle(g, x, y, w, Texts.reputation("ruf"));
        int ty = y + 15;
        TechPanel.bar(g, x, ty, w - 44, 10, (float) st.ruf(), rufColor(st.ruf()));
        panel.line(g, Texts.percent((float) st.ruf()), x + w - 40, ty + 1, 40, TechPanel.TEXT);
        ty += 16;
        if (!st.measured()) {
            panel.wrapped(g, Component.translatable("data_center_addon.reputation.ruf.unmeasured"), x, ty, w, TechPanel.TEXT_MUTED, 3);
            return;
        }
        line(g, x, ty, w, "ruf.uptime", String.format(Locale.ROOT, "%.2f %%", st.uptime() * 100));
        ty += LINE_H;
        line(g, x, ty, w, "ruf.overload", Texts.percent((float) st.overload()));
        ty += LINE_H;
        line(g, x, ty, w, "ruf.throttle", Texts.percent((float) st.throttle()));
        ty += LINE_H;
        line(g, x, ty, w, Texts.reputation("ruf.crashes"), st.crashes() + (st.incidents() > 0.0005
                ? "  (−" + Texts.percent((float) st.incidents()) + ")" : ""), st.incidents() > 0.0005 ? TechPanel.WARN : TechPanel.TEXT);
        ty += LINE_H;
        line(g, x, ty, w, "ruf.days", Integer.toString(st.days()));
        ty += LINE_H;
        if (st.slaUptime() >= 0) {
            boolean met = st.slaUptime() >= 0.999;
            line(g, x, ty, w, Texts.reputation("sla"), String.format(Locale.ROOT, "%.2f %%", st.slaUptime() * 100),
                    met ? TechPanel.GOOD : TechPanel.WARN);
            if (panel.hover(x, ty - 1, w, LINE_H)) {
                panel.tooltip(List.of(Component.translatable("data_center_addon.reputation.sla.tip").withStyle(ChatFormatting.GRAY)));
            }
            ty += LINE_H;
        }
        ty += 4;
        panel.wrapped(g, Component.translatable("data_center_addon.reputation.ruf.note"), x, ty, w, TechPanel.TEXT_MUTED, 4);
    }

    /** "Label ......... value" */
    private void line(GuiGraphics g, int x, int y, int w, String key, String value) {
        line(g, x, y, w, Texts.reputation(key), value, TechPanel.TEXT);
    }

    private void line(GuiGraphics g, int x, int y, int w, String label, String value, int color) {
        panel.line(g, label, x, y, w - 60, TechPanel.TEXT_MUTED);
        int vw = panel.font.width(value);
        g.drawString(panel.font, value, x + w - vw, y, color, false);
    }

    static int rufColor(double ruf) {
        return ruf >= 0.9 ? TechPanel.GOOD : ruf >= 0.7 ? TechPanel.WARN : TechPanel.DANGER;
    }
}
