package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static de.f4ked.data_center_addon.client.hosting.Panel.LINE_H;

/** Reputation: the account's Ruf (quality of the last 30 days) and what it is made of. */
final class ReputationTab implements ManagerTab {
    private final Panel panel;

    ReputationTab(Panel panel) {
        this.panel = panel;
    }

    @Override
    public void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        int leftW = (w - 12) / 2;
        ruf(g, s.standing(), x, y, leftW);
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
        panel.wrapped(g, Component.translatable("data_center_addon.reputation.ruf.note"), x, ty, w, TechPanel.TEXT_MUTED, 5);
    }

    /** "Label ......... value" */
    private void line(GuiGraphics g, int x, int y, int w, String key, String value) {
        panel.line(g, Texts.reputation(key), x, y, w - 60, TechPanel.TEXT_MUTED);
        int vw = panel.font.width(value);
        g.drawString(panel.font, value, x + w - vw, y, TechPanel.TEXT, false);
    }

    static int rufColor(double ruf) {
        return ruf >= 0.9 ? TechPanel.GOOD : ruf >= 0.7 ? TechPanel.WARN : TechPanel.DANGER;
    }
}
