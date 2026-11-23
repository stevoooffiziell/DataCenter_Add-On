package de.f4ked.data_center_addon.client;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.HostingManagerMenu;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingManagerService;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Hosting Manager screen, in Data Center's tech-panel look. Three tabs: an overview with the
 * totals and the products, the customer segments, and the racks, where the selected rack's product
 * and fill can be changed like in the rack's own menu.
 * <p>
 * The window grows with the screen (between {@link #MIN_W}x{@link #MIN_H} and
 * {@link #MAX_W}x{@link #MAX_H}); table columns are placed as fractions of its width.
 */
public class HostingManagerScreen extends TechPanelScreen<HostingManagerMenu> {
    private static final int MIN_W = 360;
    private static final int MAX_W = 520;
    private static final int MIN_H = 240;
    private static final int MAX_H = 320;
    private static final int SCREEN_MARGIN = 16;
    private static final int PAD = 10;
    private static final int HEADER_H = 28;
    private static final int TAB_W = 76;
    private static final int TAB_H = 16;
    private static final int ROW_H = 15;
    private static final int TEXT_DY = 4;
    private static final int CARD_H = 36;
    private static final int CONTROLS_H = 50;
    private static final int ROW_ALT = 0x14FFFFFF;
    private static final int ROW_HOVER = 0x30FFFFFF;
    private static final int ROW_SELECTED = 0x405AB0E0;
    private static final int ON_ACCENT = 0xFF101418;
    private static final int HEADER_LINE = 0x605AB0E0;

    private enum Tab { OVERVIEW, SEGMENTS, RACKS }

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    @Nullable
    private BlockPos selected;
    /** Clickable areas of the last frame. */
    private final List<Button> buttons = new ArrayList<>();

    private record Button(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public HostingManagerScreen(HostingManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
    }

    @Override
    protected void init() {
        this.imageWidth = Mth.clamp(width - 2 * SCREEN_MARGIN, MIN_W, MAX_W);
        this.imageHeight = Mth.clamp(height - 2 * SCREEN_MARGIN, MIN_H, MAX_H);
        super.init();
    }

    private HostingSnapshot snapshot() {
        return HostingManagerClient.snapshotFor(menu.managerPos());
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // drawn in renderBg, in screen coordinates
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        buttons.clear();
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TechPanel.background(g, x, y, w, h);
        g.drawString(font, title, x + PAD + 2, y + 10, TechPanel.TEXT, true);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tx = x + w - PAD - (tabs.length - i) * (TAB_W + 3) + 3;
            int ty = y + 6;
            boolean active = t == tab;
            boolean hover = inside(mouseX, mouseY, tx, ty, TAB_W, TAB_H);
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H, active ? TechPanel.ACCENT : hover ? 0xFF2A3440 : TechPanel.SLOT_BG);
            Component label = Component.translatable("data_center_addon.hosting.tab." + t.name().toLowerCase(Locale.ROOT));
            g.drawCenteredString(font, label, tx + TAB_W / 2, ty + 4, active ? ON_ACCENT : TechPanel.TEXT);
            buttons.add(new Button(tx, ty, TAB_W, TAB_H, () -> {
                tab = t;
                scroll = 0;
            }));
        }

        int cx = x + PAD, cy = y + HEADER_H, cw = w - 2 * PAD, ch = h - HEADER_H - PAD;
        g.fill(cx, cy, cx + cw, cy + ch, 0x80000000);
        HostingSnapshot s = snapshot();
        if (!HostingManagerClient.hasSnapshot(menu.managerPos())) {
            message(g, cx, cy, cw, ch, Component.translatable("data_center_addon.hosting.loading"));
            return;
        }
        if (!s.connected()) {
            message(g, cx, cy, cw, ch, Component.translatable("data_center_addon.hosting.not_connected"));
            return;
        }
        if (s.racks().isEmpty()) {
            message(g, cx, cy, cw, ch, s.foreignRacks() > 0
                    ? Component.translatable("data_center_addon.hosting.only_foreign", s.foreignRacks())
                    : Component.translatable("data_center_addon.hosting.no_racks"));
            return;
        }
        int ix = cx + 8, iy = cy + 8, iw = cw - 16, ih = ch - 16;
        switch (tab) {
            case OVERVIEW -> overview(g, s, ix, iy, iw, ih);
            case SEGMENTS -> segments(g, s, ix, iy, iw, ih, mouseX, mouseY);
            case RACKS -> racks(g, s, ix, iy, iw, ih, mouseX, mouseY);
        }
    }

    private void message(GuiGraphics g, int x, int y, int w, int h, Component text) {
        List<FormattedCharSequence> lines = font.split(text, Math.min(w - 40, 300));
        int ty = y + (h - lines.size() * 11) / 2;
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, x + w / 2, ty, TechPanel.TEXT_MUTED);
            ty += 11;
        }
    }

    // ------------------------------------------------------------------ overview

    private void overview(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h) {
        long perDay = s.totalCentsPerDay();
        String[][] cards = {
                {tr("overview.customers"), GuiText.compactNumber(Math.round(s.totalCustomers()))},
                {tr("overview.per_day"), GuiText.compactUsd(perDay)},
                {tr("overview.per_month"), GuiText.compactUsd(perDay * 30)},
                {tr("overview.racks"), s.racks().size() + (s.foreignRacks() > 0 ? " (+" + s.foreignRacks() + ")" : "")},
        };
        int gap = 6, cardW = (w - 3 * gap) / 4;
        for (int i = 0; i < cards.length; i++) {
            int bx = x + i * (cardW + gap);
            g.fill(bx, y, bx + cardW, y + CARD_H, TechPanel.SLOT_BG);
            g.fill(bx, y, bx + 2, y + CARD_H, TechPanel.ACCENT);
            g.drawString(font, GuiText.fit(font, cards[i][0], cardW - 12), bx + 8, y + 6, TechPanel.TEXT_MUTED, false);
            g.drawString(font, GuiText.fit(font, cards[i][1], cardW - 12), bx + 8, y + 20,
                    i == 1 || i == 2 ? TechPanel.GOOD : TechPanel.TEXT, false);
        }

        // reputation and network: label | bar | value
        int labelW = 78, valueW = Math.max(130, w / 3);
        int barX = x + labelW, barW = w - labelW - valueW - 8;
        int ry = y + CARD_H + 12;
        g.drawString(font, tr("overview.reputation"), x, ry + 1, TechPanel.TEXT_MUTED, false);
        TechPanel.bar(g, barX, ry, barW, 10, s.reputation() / 1000f, TechPanel.ACCENT);
        String rep = s.reputation() + " / 1000 · " + Component.translatable(CustomerClass.ofOrdinal(s.customerClass()).translationKey()).getString();
        g.drawString(font, GuiText.fit(font, rep, valueW), barX + barW + 8, ry + 1, TechPanel.TEXT, false);

        int ny = ry + 16;
        HostingSnapshot.Network net = s.network();
        g.drawString(font, tr("overview.network"), x, ny + 1, TechPanel.TEXT_MUTED, false);
        boolean limited = net.capacity() != Long.MAX_VALUE;
        float used = !limited ? 0 : net.capacity() <= 0 ? 1 : (float) net.delivered() / net.capacity();
        TechPanel.bar(g, barX, ny, barW, 10, limited ? used : 0, used > 0.95f ? TechPanel.WARN : TechPanel.OUTPUT);
        String netText = GuiText.compactNumber(net.delivered()) + " / " + (limited ? GuiText.compactNumber(net.capacity()) : "∞")
                + " " + tr("overview.compute");
        g.drawString(font, GuiText.fit(font, netText, valueW), barX + barW + 8, ny + 1, TechPanel.TEXT, false);
        if (net.unpoweredSwitches() > 0 || net.overlongRuns() > 0) {
            ny += 14;
            Component warning = net.unpoweredSwitches() > 0
                    ? Component.translatable("data_center_addon.hosting.overview.unpowered", net.unpoweredSwitches())
                    : Component.translatable("data_center_addon.hosting.overview.overlong", net.overlongRuns());
            g.drawString(font, GuiText.fit(font, "⚠ " + warning.getString(), w), x, ny + 1,
                    net.unpoweredSwitches() > 0 ? TechPanel.DANGER : TechPanel.WARN, false);
        }

        // per product
        int ty = ny + 22;
        int[] col = columns(x, w, 0, 0.40, 0.52, 0.66, 0.86);
        header(g, x, ty, w, col, "column.product", "column.racks", "column.customers", "column.load", "column.per_day");
        ty += ROW_H + 2;
        Map<Integer, double[]> byProduct = new LinkedHashMap<>();
        for (HostingSnapshot.Rack rack : s.racks()) {
            double[] sum = byProduct.computeIfAbsent(rack.product(), p -> new double[4]);
            sum[0]++;
            sum[1] += rack.customers();
            sum[2] += rack.capacity();
            sum[3] += rack.centsPerDay();
        }
        int row = 0;
        for (Map.Entry<Integer, double[]> entry : byProduct.entrySet()) {
            if (ty + ROW_H > y + h) {
                break;
            }
            double[] sum = entry.getValue();
            stripe(g, x, ty, w, row++);
            text(g, productName(entry.getKey()).getString(), col, 0, ty, TechPanel.TEXT);
            text(g, Integer.toString((int) sum[0]), col, 1, ty, TechPanel.TEXT);
            text(g, GuiText.compactNumber(Math.round(sum[1])), col, 2, ty, TechPanel.TEXT);
            loadBar(g, col[3], ty + 3, col[4] - col[3] - 12, sum[2] <= 0 ? 0 : (float) (sum[1] / sum[2]));
            text(g, GuiText.compactUsd((long) sum[3]), col, 4, ty, TechPanel.GOOD);
            ty += ROW_H;
        }
    }

    // ------------------------------------------------------------------ segments

    private void segments(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        int[] col = columns(x, w, 0, 0.40, 0.56, 0.84);
        header(g, x, y, w, col, "column.segment", "column.customers", "column.share", "column.per_day");
        if (s.segments().isEmpty()) {
            message(g, x, y + ROW_H, w, h - ROW_H, Component.translatable("data_center_addon.hosting.no_segments"));
            return;
        }
        double total = 0;
        for (HostingSnapshot.Segment segment : s.segments()) {
            total += segment.customers();
        }
        List<HostingSnapshot.Segment> list = s.segments();
        int noteH = 14;
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - noteH) / ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - visible));
        int ty = top;
        int shareBarW = col[3] - col[2] - 56;
        for (int i = scroll; i < list.size() && i < scroll + visible; i++) {
            HostingSnapshot.Segment segment = list.get(i);
            CustomerSegment kind = CustomerSegment.values()[Mth.clamp(segment.segment(), 0, CustomerSegment.values().length - 1)];
            float share = total <= 0 ? 0 : (float) (segment.customers() / total);
            stripe(g, x, ty, w, i);
            text(g, Component.translatable(kind.translationKey()).getString(), col, 0, ty, TechPanel.TEXT);
            text(g, GuiText.compactNumber(Math.round(segment.customers())), col, 1, ty, TechPanel.TEXT);
            TechPanel.bar(g, col[2], ty + 3, shareBarW, 9, share, TechPanel.VIOLET);
            g.drawString(font, percent(share), col[2] + shareBarW + 6, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            text(g, GuiText.compactUsd(segment.centsPerDay()), col, 3, ty, TechPanel.GOOD);
            ty += ROW_H;
        }
        scrollbar(g, x + w + 3, top, visible * ROW_H, list.size(), visible);
        g.drawString(font, GuiText.fit(font, tr("segments.note"), w), x, y + h - 9, TechPanel.TEXT_MUTED, false);
    }

    // ------------------------------------------------------------------ racks

    private void racks(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        int[] col = columns(x, w, 0, 0.19, 0.44, 0.60, 0.71, 0.84);
        header(g, x, y, w, col, "column.rack", "column.product", "column.customers", "column.share", "column.per_day", "column.status");
        double total = s.totalCustomers();
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - CONTROLS_H) / ROW_H;
        List<HostingSnapshot.Rack> list = s.racks();
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - visible));
        HostingSnapshot.Rack current = null;
        for (HostingSnapshot.Rack rack : list) {
            if (rack.pos().equals(selected)) {
                current = rack;
            }
        }
        int ty = top;
        for (int i = scroll; i < list.size() && i < scroll + visible; i++) {
            HostingSnapshot.Rack rack = list.get(i);
            boolean isSelected = rack.pos().equals(selected);
            if (isSelected) {
                g.fill(x - 4, ty, x + w + 4, ty + ROW_H, ROW_SELECTED);
            } else if (inside(mouseX, mouseY, x, ty, w, ROW_H)) {
                g.fill(x - 4, ty, x + w + 4, ty + ROW_H, ROW_HOVER);
            } else {
                stripe(g, x, ty, w, i);
            }
            BlockPos p = rack.pos();
            text(g, p.getX() + ", " + p.getY() + ", " + p.getZ(), col, 0, ty, TechPanel.TEXT_MUTED);
            text(g, productName(rack.product()).getString(), col, 1, ty, TechPanel.TEXT);
            text(g, GuiText.compactNumber(Math.round(rack.customers())) + " / " + GuiText.compactNumber(Math.round(rack.capacity())),
                    col, 2, ty, TechPanel.TEXT);
            text(g, percent(total <= 0 ? 0 : (float) (rack.customers() / total)), col, 3, ty, TechPanel.TEXT_MUTED);
            text(g, GuiText.compactUsd(rack.centsPerDay()), col, 4, ty, TechPanel.GOOD);
            g.drawString(font, GuiText.fit(font, statusName(rack.status()).getString(), x + w - col[5]), col[5], ty + TEXT_DY,
                    rack.status() == 0 ? TechPanel.GOOD : TechPanel.WARN, false);
            buttons.add(new Button(x, ty, w, ROW_H, () -> selected = rack.pos()));
            ty += ROW_H;
        }
        scrollbar(g, x + w + 3, top, visible * ROW_H, list.size(), visible);

        // controls for the selected rack
        int py = y + h - CONTROLS_H + 8;
        g.fill(x - 4, py - 6, x + w + 4, py - 5, TechPanel.ACCENT);
        if (current == null) {
            g.drawString(font, tr("racks.select"), x, py + 14, TechPanel.TEXT_MUTED, false);
            return;
        }
        BlockPos p = current.pos();
        g.drawString(font, Component.translatable("data_center_addon.hosting.racks.selected", p.getX(), p.getY(), p.getZ()),
                x, py, TechPanel.TEXT, false);
        HostingSnapshot.Rack rack = current;
        int by = py + 16;
        String productLabel = tr("column.product");
        int labelW = font.width(productLabel);
        g.drawString(font, productLabel, x, by + 4, TechPanel.TEXT_MUTED, false);
        int productW = Math.min(180, w / 2 - labelW - 16);
        stepper(g, x + labelW + 8, by, productW, productName(rack.product()).getString(), mouseX, mouseY,
                () -> send(rack, HostingManagerService.Action.PRODUCT_PREVIOUS), () -> send(rack, HostingManagerService.Action.PRODUCT_NEXT));
        if (hasFill(rack.product())) {
            String fillLabel = tr("racks.fill");
            int fx = x + w / 2 + 8;
            g.drawString(font, fillLabel, fx, by + 4, TechPanel.TEXT_MUTED, false);
            stepper(g, fx + font.width(fillLabel) + 8, by, 80, rack.fill() + " %", mouseX, mouseY,
                    () -> send(rack, HostingManagerService.Action.FILL_DOWN), () -> send(rack, HostingManagerService.Action.FILL_UP));
        }
    }

    /** "◀ value ▶" with two clickable arrows. */
    private void stepper(GuiGraphics g, int x, int y, int w, String value, int mouseX, int mouseY, Runnable previous, Runnable next) {
        int size = 16;
        arrow(g, x, y, size, "◀", mouseX, mouseY, previous);
        g.fill(x + size + 2, y, x + w - size - 2, y + size, TechPanel.SLOT_BG);
        g.drawCenteredString(font, GuiText.fit(font, value, w - 2 * size - 10), x + w / 2, y + 4, TechPanel.TEXT);
        arrow(g, x + w - size, y, size, "▶", mouseX, mouseY, next);
    }

    private void arrow(GuiGraphics g, int x, int y, int size, String glyph, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, size, size);
        g.fill(x, y, x + size, y + size, hover ? TechPanel.ACCENT : TechPanel.SLOT_BG);
        g.drawCenteredString(font, glyph, x + size / 2, y + 4, hover ? ON_ACCENT : TechPanel.TEXT);
        buttons.add(new Button(x, y, size, size, action));
    }

    private void send(HostingSnapshot.Rack rack, HostingManagerService.Action action) {
        PacketDistributor.sendToServer(new HostingManagerPayloads.Change(menu.managerPos(), rack.pos(), action));
    }

    // ------------------------------------------------------------------ helpers

    /** Column start positions as fractions of the table width. */
    private static int[] columns(int x, int w, double... fractions) {
        int[] col = new int[fractions.length];
        for (int i = 0; i < fractions.length; i++) {
            col[i] = x + (int) Math.round(w * fractions[i]);
        }
        return col;
    }

    private void header(GuiGraphics g, int x, int y, int w, int[] col, String... keys) {
        for (int i = 0; i < keys.length && i < col.length; i++) {
            int maxW = (i + 1 < col.length ? col[i + 1] : x + w) - col[i] - 6;
            g.drawString(font, GuiText.fit(font, tr(keys[i]), maxW), col[i], y + TEXT_DY, TechPanel.ACCENT, false);
        }
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, HEADER_LINE);
    }

    /** A cell's text, cut to fit its column. */
    private void text(GuiGraphics g, String value, int[] col, int index, int rowY, int color) {
        int maxW = (index + 1 < col.length ? col[index + 1] : col[index] + 200) - col[index] - 8;
        g.drawString(font, GuiText.fit(font, value, maxW), col[index], rowY + TEXT_DY, color, false);
    }

    private static void stripe(GuiGraphics g, int x, int y, int w, int row) {
        if (row % 2 == 1) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_ALT);
        }
    }

    private void scrollbar(GuiGraphics g, int x, int y, int trackH, int total, int visible) {
        if (total <= visible || trackH <= 0) {
            return;
        }
        int thumbH = Math.max(10, trackH * visible / total);
        int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, total - visible);
        g.fill(x, y, x + 2, y + trackH, TechPanel.SLOT_BG);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, TechPanel.ACCENT);
    }

    private void loadBar(GuiGraphics g, int x, int y, int w, float ratio) {
        TechPanel.bar(g, x, y, Math.max(20, w), 9, ratio, ratio > 0.95f ? TechPanel.WARN : TechPanel.GOOD);
    }

    private static Component productName(int product) {
        if (product < 0 || product >= HostingProduct.values().length) {
            return Component.translatable(HostingMode.DEDICATED.translationKey());
        }
        return Component.translatable(HostingProduct.values()[product].translationKey());
    }

    private static boolean hasFill(int product) {
        if (product < 0 || product >= HostingProduct.values().length) {
            return false;
        }
        HostingMode mode = HostingProduct.values()[product].mode();
        return mode == HostingMode.SHARED || mode == HostingMode.VPS;
    }

    private static Component statusName(int status) {
        String[] keys = {"ok", "no_panel", "no_hypervisor", "moving", "no_server", "no_game_os"};
        return Component.translatable("data_center_addon.hosting.status." + (status >= 0 && status < keys.length ? keys[status] : "ok"));
    }

    private static String percent(float share) {
        return String.format(Locale.ROOT, "%.1f %%", share * 100);
    }

    private static String tr(String key) {
        return Component.translatable("data_center_addon.hosting." + key).getString();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Button b : List.copyOf(buttons)) {
                if (b.contains(mouseX, mouseY)) {
                    b.action.run();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
