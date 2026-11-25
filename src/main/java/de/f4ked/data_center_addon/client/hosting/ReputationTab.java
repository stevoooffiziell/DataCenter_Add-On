package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.hosting.CustomerClass;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.Renown;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static de.f4ked.data_center_addon.client.hosting.Panel.LINE_H;

/**
 * Reputation: the account's Ruf (quality of the last 30 days) on the left, its Bekanntheit (renown) and
 * where it came from on the right.
 */
final class ReputationTab implements ManagerTab {
    private final Panel panel;

    ReputationTab(Panel panel) {
        this.panel = panel;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int leftW = (w - 12) / 2;
        int classH = 36;
        ruf(g, s.standing(), x, y, leftW);
        g.fill(x + leftW + 5, y, x + leftW + 6, y + h - classH - 4, Panel.HEADER_LINE);
        renown(g, s.standing(), x + leftW + 12, y, w - leftW - 12);
        classes(g, s, x, y + h - classH, w);
    }

    /** The customer class and what the next one needs: Ruf, renown and the room's tier. */
    private void classes(GuiGraphics g, HostingSnapshot s, int x, int y, int w) {
        CustomerClass current = CustomerClass.ofOrdinal(s.customerClass());
        panel.sectionTitle(g, x, y, w, Component.translatable("data_center_addon.reputation.class",
                Component.translatable(current.translationKey())).getString());
        int ty = y + 15;
        if (current.ordinal() >= CustomerClass.values().length - 1) {
            panel.line(g, Texts.reputation("class.top"), x, ty, w, TechPanel.GOOD);
            return;
        }
        CustomerClass next = CustomerClass.ofOrdinal(current.ordinal() + 1);
        HostingSnapshot.Standing st = s.standing();
        double needRuf = ClassRules.RUF[next.ordinal()], needRenown = ClassRules.RENOWN[next.ordinal()];
        boolean rufOk = st.ruf() >= needRuf - 1e-9, renownOk = st.renown() >= needRenown;
        int tx = x;
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.next",
                Component.translatable(next.translationKey())).getString() + "  ", TechPanel.TEXT);
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.ruf", Texts.percent((float) st.ruf()),
                Texts.percent((float) needRuf)).getString() + (rufOk ? " ✔" : " ✘") + "  ", rufOk ? TechPanel.GOOD : TechPanel.WARN);
        tx = part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.renown", Math.round(st.renown()),
                Math.round(needRenown)).getString() + (renownOk ? " ✔" : " ✘") + "  ", renownOk ? TechPanel.GOOD : TechPanel.WARN);
        part(g, tx, ty, Component.translatable("data_center_addon.reputation.class.tier",
                Component.translatable(next.requiredTier.translationKey())).getString(), TechPanel.TEXT_MUTED);
        if (panel.hover(x, y, w, 26)) {
            panel.tooltip(List.of(Component.translatable("data_center_addon.reputation.class.tip").withStyle(ChatFormatting.GRAY)));
        }
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
        TechPanel.bar(g, x, ty, w - 44, 10, (float) (st.renown() / 1000), TechPanel.ACCENT);
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
        ty += 4;
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
        line(g, x, ty, w, "ruf.crashes", Integer.toString(st.crashes()));
        ty += LINE_H;
        line(g, x, ty, w, "ruf.days", Integer.toString(st.days()));
        ty += LINE_H + 4;
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
