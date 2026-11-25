package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing kit of the Hosting Manager screen, rebuilt every frame: tables, buttons, steppers and the
 * tooltip, in Data Center's tech-panel look. It knows the mouse position of the frame, so the tabs do
 * not pass it around, and collects the clickable areas the screen checks on a click.
 */
final class Panel {
    static final int ROW_H = 15;
    static final int TEXT_DY = 4;
    static final int LINE_H = 11;
    static final int ROW_ALT = 0x14FFFFFF;
    static final int ROW_HOVER = 0x30FFFFFF;
    static final int ROW_SELECTED = 0x405AB0E0;
    static final int ON_ACCENT = 0xFF101418;
    static final int HEADER_LINE = 0x605AB0E0;
    static final int HOVER_BG = 0xFF2A3440;
    /** Background of a button that does the main thing (accept, deliver, take). */
    static final int BUTTON_PRIMARY = 0xFF2E6E8E;
    /** Background of a button that cannot be used right now. */
    static final int BUTTON_OFF = 0xFF1A2028;

    private record Button(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    /** Set by the screen in {@code init} (the screen's font does not exist before). */
    Font font;
    /** First visible row of the scrolling table of the current tab. */
    int scroll;
    private final List<Button> buttons = new ArrayList<>();
    @Nullable
    private List<Component> tooltip;
    private int mouseX, mouseY;

    // ------------------------------------------------------------------ frame

    void begin(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        buttons.clear();
        tooltip = null;
    }

    @Nullable
    List<Component> tooltip() {
        return tooltip;
    }

    void tooltip(List<Component> lines) {
        tooltip = lines;
    }

    boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    /** A clickable area for this frame. */
    void clickable(int x, int y, int w, int h, Runnable action) {
        buttons.add(new Button(x, y, w, h, action));
    }

    /** Runs the action of the clicked area; whether there was one. */
    boolean click(double mx, double my) {
        for (Button b : List.copyOf(buttons)) {
            if (b.contains(mx, my)) {
                b.action.run();
                return true;
            }
        }
        return false;
    }

    static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    // ------------------------------------------------------------------ text

    /** A message centred in the area. */
    void message(GuiGraphics g, int x, int y, int w, int h, Component text) {
        List<FormattedCharSequence> lines = font.split(text, Math.min(w - 40, 300));
        int ty = y + (h - lines.size() * LINE_H) / 2;
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, x + w / 2, ty, TechPanel.TEXT_MUTED);
            ty += LINE_H;
        }
    }

    /** One line, cut to {@code w}. */
    void line(GuiGraphics g, String text, int x, int y, int w, int color) {
        g.drawString(font, GuiText.fit(font, text, w), x, y, color, false);
    }

    /** Text wrapped to {@code w}, at most {@code maxLines} lines; returns the y below it. */
    int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.drawString(font, lines.get(i), x, y, color, false);
            y += LINE_H;
        }
        return y;
    }

    /** A blue section title with a line under it. */
    void sectionTitle(GuiGraphics g, int x, int y, int w, String title) {
        line(g, title, x, y, w, TechPanel.ACCENT);
        g.fill(x, y + 10, x + w, y + 11, HEADER_LINE);
    }

    // ------------------------------------------------------------------ tables

    /** Column start positions as fractions of the table width. */
    static int[] columns(int x, int w, double... fractions) {
        int[] col = new int[fractions.length];
        for (int i = 0; i < fractions.length; i++) {
            col[i] = x + (int) Math.round(w * fractions[i]);
        }
        return col;
    }

    /** Column titles ({@code data_center_addon.hosting.*} keys) with a line under them. */
    void header(GuiGraphics g, int x, int y, int w, int[] col, String... keys) {
        for (int i = 0; i < keys.length && i < col.length; i++) {
            int maxW = (i + 1 < col.length ? col[i + 1] : x + w) - col[i] - 6;
            line(g, Texts.hosting(keys[i]), col[i], y + TEXT_DY, maxW, TechPanel.ACCENT);
        }
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, HEADER_LINE);
    }

    /** A cell's text, cut to fit its column. */
    void cell(GuiGraphics g, String value, int[] col, int index, int rowY, int color) {
        int maxW = (index + 1 < col.length ? col[index + 1] : col[index] + 200) - col[index] - 8;
        line(g, value, col[index], rowY + TEXT_DY, maxW, color);
    }

    /** Every second row slightly lighter. */
    static void stripe(GuiGraphics g, int x, int y, int w, int row) {
        if (row % 2 == 1) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_ALT);
        }
    }

    /** Background of a table row: selected, hovered or striped; returns whether it is hovered. */
    boolean row(GuiGraphics g, int x, int y, int w, int index, boolean selected) {
        boolean hovered = hover(x, y, w, ROW_H);
        if (selected) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_SELECTED);
        } else if (hovered) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_HOVER);
        } else {
            stripe(g, x, y, w, index);
        }
        return hovered;
    }

    /** Keeps {@link #scroll} within a list of {@code total} rows of which {@code visible} fit. */
    void clampScroll(int total, int visible) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - visible)));
    }

    void scrollbar(GuiGraphics g, int x, int y, int trackH, int total, int visible) {
        if (total <= visible || trackH <= 0) {
            return;
        }
        int thumbH = Math.max(10, trackH * visible / total);
        int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, total - visible);
        g.fill(x, y, x + 2, y + trackH, TechPanel.SLOT_BG);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, TechPanel.ACCENT);
    }

    /**
     * A line chart of {@code values} between {@code min} and {@code max}, left to right, drawn as small
     * squares along the line between the points.
     */
    static void chart(GuiGraphics g, int x, int y, int w, int h, List<Double> values, double min, double max, int color) {
        if (values.size() < 2 || max <= min) {
            return;
        }
        int n = values.size();
        double px = x, py = y + h - (values.get(0) - min) / (max - min) * h;
        for (int i = 1; i < n; i++) {
            double nx = x + (double) w * i / (n - 1);
            double ny = y + h - (Math.max(min, Math.min(max, values.get(i))) - min) / (max - min) * h;
            int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(nx - px), Math.abs(ny - py))));
            for (int s = 0; s <= steps; s++) {
                int cx = (int) Math.round(px + (nx - px) * s / steps);
                int cy = (int) Math.round(py + (ny - py) * s / steps);
                g.fill(cx, cy, cx + 1, cy + 1, color);
            }
            px = nx;
            py = ny;
        }
    }

    /** Green up to 95 % load, amber above. */
    static void loadBar(GuiGraphics g, int x, int y, int w, float ratio) {
        TechPanel.bar(g, x, y, Math.max(20, w), 9, ratio, ratio > 0.95f ? TechPanel.WARN : TechPanel.GOOD);
    }

    // ------------------------------------------------------------------ controls

    /** A small labelled button; {@code action == null} draws it disabled. */
    void button(GuiGraphics g, int x, int y, int w, String label, int background, @Nullable Runnable action) {
        int h = ROW_H - 2;
        boolean hovered = action != null && hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? TechPanel.ACCENT : background);
        g.drawCenteredString(font, GuiText.fit(font, label, w - 6), x + w / 2, y + 3,
                hovered ? ON_ACCENT : action == null ? TechPanel.TEXT_MUTED : TechPanel.TEXT);
        if (action != null) {
            clickable(x, y, w, h, action);
        }
    }

    /** One half of a two-way switch; picking it also resets the scrolling. */
    void toggle(GuiGraphics g, int x, int y, int w, String label, boolean active, Runnable action) {
        int h = ROW_H - 2;
        g.fill(x, y, x + w, y + h, active ? HEADER_LINE : hover(x, y, w, h) ? HOVER_BG : TechPanel.SLOT_BG);
        if (active) {
            g.fill(x, y + h - 1, x + w, y + h, TechPanel.ACCENT);
        }
        g.drawCenteredString(font, label, x + w / 2, y + 3, active ? TechPanel.TEXT : TechPanel.TEXT_MUTED);
        clickable(x, y, w, h, () -> {
            action.run();
            scroll = 0;
        });
    }

    /** "◀ value ▶" with two clickable arrows. */
    void stepper(GuiGraphics g, int x, int y, int w, String value, Runnable previous, Runnable next) {
        int size = 16;
        arrow(g, x, y, size, "◀", previous);
        g.fill(x + size + 2, y, x + w - size - 2, y + size, TechPanel.SLOT_BG);
        g.drawCenteredString(font, GuiText.fit(font, value, w - 2 * size - 10), x + w / 2, y + 4, TechPanel.TEXT);
        arrow(g, x + w - size, y, size, "▶", next);
    }

    private void arrow(GuiGraphics g, int x, int y, int size, String glyph, Runnable action) {
        boolean hovered = hover(x, y, size, size);
        g.fill(x, y, x + size, y + size, hovered ? TechPanel.ACCENT : TechPanel.SLOT_BG);
        g.drawCenteredString(font, glyph, x + size / 2, y + 4, hovered ? ON_ACCENT : TechPanel.TEXT);
        clickable(x, y, size, size, action);
    }
}
