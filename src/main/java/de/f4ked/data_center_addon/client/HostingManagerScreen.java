package de.f4ked.data_center_addon.client;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.ContractService;
import de.f4ked.data_center_addon.hosting.DeliveryService;
import de.f4ked.data_center_addon.hosting.HostingManagerMenu;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.hosting.HostingManagerService;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Milestone;
import de.f4ked.data_center_addon.hosting.StartupService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
    private static final int MIN_W = 380;
    private static final int MAX_W = 520;
    private static final int MIN_H = 240;
    private static final int MAX_H = 320;
    private static final int SCREEN_MARGIN = 16;
    private static final int PAD = 10;
    private static final int HEADER_H = 28;
    private static final int TAB_W_MIN = 46;
    private static final int TAB_W_MAX = 64;
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

    private enum Tab { OVERVIEW, SEGMENTS, RACKS, CONTRACTS, STARTUP }

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    @Nullable
    private BlockPos selected;
    /** Contract whose cancel button was clicked once, and until when the second click confirms it. */
    private int confirmCancel = -1;
    private long confirmUntil;
    /** Until when a second click on "take loan" takes it. */
    private long confirmLoanUntil;
    /** Contracts tab: delivery orders (true) or hosting reservations; the delivery shown in the detail. */
    private boolean deliveriesView = true;
    private int selectedDelivery = -1;
    /** Tooltip of the last frame, drawn on top of everything in {@link #render}. */
    @Nullable
    private List<Component> tooltip;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        buttons.clear();
        tooltip = null;
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TechPanel.background(g, x, y, w, h);
        g.drawString(font, title, x + PAD + 2, y + 10, TechPanel.TEXT, true);
        Tab[] tabs = Tab.values();
        int tabW = Mth.clamp((w - 2 * PAD - font.width(title) - 16) / tabs.length - 3, TAB_W_MIN, TAB_W_MAX);
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tx = x + w - PAD - (tabs.length - i) * (tabW + 3) + 3;
            int ty = y + 6;
            boolean active = t == tab;
            boolean hover = inside(mouseX, mouseY, tx, ty, tabW, TAB_H);
            g.fill(tx, ty, tx + tabW, ty + TAB_H, active ? TechPanel.ACCENT : hover ? 0xFF2A3440 : TechPanel.SLOT_BG);
            String label = Component.translatable("data_center_addon.hosting.tab." + t.name().toLowerCase(Locale.ROOT)).getString();
            g.drawCenteredString(font, GuiText.fit(font, label, tabW - 4), tx + tabW / 2, ty + 4, active ? ON_ACCENT : TechPanel.TEXT);
            buttons.add(new Button(tx, ty, tabW, TAB_H, () -> {
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
        if (!s.connected() && tab != Tab.STARTUP) {
            message(g, cx, cy, cw, ch, Component.translatable("data_center_addon.hosting.not_connected"));
            return;
        }
        if (s.racks().isEmpty() && tab != Tab.CONTRACTS && tab != Tab.STARTUP) {
            message(g, cx, cy, cw, ch, s.foreignRacks() > 0
                    ? Component.translatable("data_center_addon.hosting.only_foreign", s.foreignRacks())
                    : Component.translatable("data_center_addon.hosting.no_racks"));
            return;
        }
        int ix = cx + 8, iy = cy + 8, iw = cw - 16, ih = ch - 16;
        switch (tab) {
            case OVERVIEW -> overview(g, s, ix, iy, iw, ih, mouseX, mouseY);
            case SEGMENTS -> segments(g, s, ix, iy, iw, ih, mouseX, mouseY);
            case RACKS -> racks(g, s, ix, iy, iw, ih, mouseX, mouseY);
            case CONTRACTS -> contracts(g, s, ix, iy, iw, ih, mouseX, mouseY);
            case STARTUP -> startup(g, s.startup(), ix, iy, iw, ih, mouseX, mouseY);
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

    private void overview(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        // income including the running start bonus; the hour is a real hour, as in the rack's own menu
        long bonusPerDay = s.startBonusCentsPerDay();
        long perDay = s.incomeCentsPerDay() + bonusPerDay;
        String[][] cards = {
                {tr("overview.customers"), GuiText.compactNumber(Math.round(s.totalCustomers()))},
                {tr("overview.per_hour"), GuiText.compactUsd(s.perHour(perDay))},
                {tr("overview.per_day"), GuiText.compactUsd(perDay)},
                {tr("overview.per_month"), GuiText.compactUsd(perDay * 30)},
                {tr("overview.racks"), s.racks().size() + (s.foreignRacks() > 0 ? " (+" + s.foreignRacks() + ")" : "")},
        };
        int gap = 6, cardW = (w - (cards.length - 1) * gap) / cards.length;
        for (int i = 0; i < cards.length; i++) {
            int bx = x + i * (cardW + gap);
            g.fill(bx, y, bx + cardW, y + CARD_H, TechPanel.SLOT_BG);
            g.fill(bx, y, bx + 2, y + CARD_H, TechPanel.ACCENT);
            g.drawString(font, GuiText.fit(font, cards[i][0], cardW - 12), bx + 8, y + 6, TechPanel.TEXT_MUTED, false);
            g.drawString(font, GuiText.fit(font, cards[i][1], cardW - 12), bx + 8, y + 20,
                    i >= 1 && i <= 3 ? TechPanel.GOOD : TechPanel.TEXT, false);
            if (i >= 1 && i <= 3 && inside(mouseX, mouseY, bx, y, cardW, CARD_H)) {
                tooltip = List.of(Component.literal(cards[i][0] + ": " + cards[i][1]),
                        Component.translatable("data_center_addon.hosting.overview.time_note").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        int ry = y + CARD_H + 12;
        if (bonusPerDay > 0) {
            String bonus = Component.translatable("data_center_addon.hosting.overview.bonus",
                    "+" + Math.round(s.startup().bonusFactor() * 100) + " %", GuiText.compactUsd(s.perHour(bonusPerDay)),
                    GuiText.compactUsd(bonusPerDay)).getString();
            g.drawString(font, GuiText.fit(font, bonus, w), x, y + CARD_H + 4, TechPanel.GOOD, false);
            ry += 8;
        }

        // reputation and network: label | bar | value
        int labelW = 78, valueW = Math.max(130, w / 3);
        int barX = x + labelW, barW = w - labelW - valueW - 8;
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
        int licenceBound = s.licenceBoundRacks();
        if (licenceBound > 0) {
            ny += 14;
            Component warning = Component.literal("⚠ ")
                    .append(Component.translatable("data_center_addon.hosting.overview.licence", licenceBound));
            List<FormattedCharSequence> lines = font.split(warning, w);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                g.drawString(font, lines.get(i), x, ny + 1 + i * 10, TechPanel.WARN, false);
            }
            ny += Math.min(lines.size(), 2) * 10 - 10;
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
        // running contracts: the places they reserve and what they pay
        List<HostingSnapshot.Contract> active = s.activeContracts();
        if (!active.isEmpty() && ty + ROW_H <= y + h) {
            stripe(g, x, ty, w, row);
            text(g, Component.translatable("data_center_addon.hosting.overview.contracts", active.size()).getString(), col, 0, ty,
                    TechPanel.ACCENT);
            text(g, "—", col, 1, ty, TechPanel.TEXT_MUTED);
            text(g, GuiText.compactNumber(s.contractAccounts()), col, 2, ty, TechPanel.TEXT);
            long covered = active.stream().filter(HostingSnapshot.Contract::covered).count();
            loadBar(g, col[3], ty + 3, col[4] - col[3] - 12, (float) covered / active.size());
            text(g, GuiText.compactUsd(s.contractCentsPerDay()), col, 4, ty, TechPanel.GOOD);
        }
    }

    // ------------------------------------------------------------------ segments

    private void segments(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        int[] col = columns(x, w, 0, 0.40, 0.56, 0.84);
        header(g, x, y, w, col, "column.segment", "column.customers", "column.share", "column.per_day");
        // customer segments of the racks, then one row per running contract (its client and reserved places)
        record Row(String name, double customers, long centsPerDay, boolean contract) {
        }
        List<Row> list = new ArrayList<>();
        for (HostingSnapshot.Segment segment : s.segments()) {
            CustomerSegment kind = CustomerSegment.values()[Mth.clamp(segment.segment(), 0, CustomerSegment.values().length - 1)];
            list.add(new Row(Component.translatable(kind.translationKey()).getString(), segment.customers(), segment.centsPerDay(), false));
        }
        for (HostingSnapshot.Contract c : s.activeContracts()) {
            list.add(new Row(Component.translatable("data_center_addon.hosting.segments.contract", clientName(c.client())).getString(),
                    c.accounts(), c.centsPerDay(), true));
        }
        if (list.isEmpty()) {
            message(g, x, y + ROW_H, w, h - ROW_H, Component.translatable("data_center_addon.hosting.no_segments"));
            return;
        }
        double total = 0;
        for (Row r : list) {
            total += r.customers;
        }
        int noteH = 14;
        int top = y + ROW_H + 2;
        int visible = (h - ROW_H - 2 - noteH) / ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - visible));
        int ty = top;
        int shareBarW = col[3] - col[2] - 56;
        for (int i = scroll; i < list.size() && i < scroll + visible; i++) {
            Row r = list.get(i);
            float share = total <= 0 ? 0 : (float) (r.customers / total);
            stripe(g, x, ty, w, i);
            text(g, r.name, col, 0, ty, r.contract ? TechPanel.ACCENT : TechPanel.TEXT);
            text(g, GuiText.compactNumber(Math.round(r.customers)), col, 1, ty, TechPanel.TEXT);
            TechPanel.bar(g, col[2], ty + 3, shareBarW, 9, share, r.contract ? TechPanel.ACCENT : TechPanel.VIOLET);
            g.drawString(font, percent(share), col[2] + shareBarW + 6, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            text(g, GuiText.compactUsd(r.centsPerDay), col, 3, ty, TechPanel.GOOD);
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
            Component status = rack.licenceBound()
                    ? Component.translatable("data_center_addon.hosting.status.licence")
                    : statusName(rack.status());
            g.drawString(font, GuiText.fit(font, status.getString(), x + w - col[5]), col[5], ty + TEXT_DY,
                    rack.status() == 0 && !rack.licenceBound() ? TechPanel.GOOD : TechPanel.WARN, false);
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
        Component selectedLabel = Component.translatable("data_center_addon.hosting.racks.selected", p.getX(), p.getY(), p.getZ());
        g.drawString(font, selectedLabel, x, py, TechPanel.TEXT, false);
        HostingSnapshot.Rack rack = current;
        if (rack.licenceBound()) {
            int hx = x + font.width(selectedLabel) + 10;
            g.drawString(font, GuiText.fit(font, tr("racks.licence_hint"), x + w - hx), hx, py, TechPanel.WARN, false);
        }
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

    // ------------------------------------------------------------------ startup

    /** The Startup tab: start bonus, grant, setup fees and founding loan; milestones; service tickets. */
    private void startup(GuiGraphics g, HostingSnapshot.Startup st, int x, int y, int w, int h, int mouseX, int mouseY) {
        int ticketRows = Math.max(1, st.tickets().size());
        int ticketsH = ROW_H + 2 + ticketRows * ROW_H;
        int topH = h - ticketsH - 8;
        int leftW = (int) (w * 0.56);
        int rightX = x + leftW + 10, rightW = w - leftW - 10;
        int line = 11;

        // left: start bonus, grant, setup fees
        int ty = y;
        sectionTitle(g, x, ty, leftW, trs("bonus"));
        ty += 13;
        String bonus;
        int bonusColor = TechPanel.TEXT;
        if (st.veteran()) {
            bonus = trs("bonus.veteran");
            bonusColor = TechPanel.TEXT_MUTED;
        } else if (!st.started()) {
            bonus = Component.translatable("data_center_addon.startup.bonus.waiting", percentBonus(st.bonusFactor())).getString();
        } else if (st.bonusFactor() > 0) {
            bonus = st.bonusMode() == 0
                    ? Component.translatable("data_center_addon.startup.bonus.time", percentBonus(st.bonusFactor()), st.bonusDaysLeft()).getString()
                    : Component.translatable("data_center_addon.startup.bonus.revenue", percentBonus(st.bonusFactor()),
                    GuiText.compactUsd(st.bonusRevenueLeft())).getString();
            bonusColor = TechPanel.GOOD;
        } else {
            bonus = trs("bonus.ended");
            bonusColor = TechPanel.TEXT_MUTED;
        }
        ty = wrapped(g, bonus, x, ty, leftW, bonusColor, 2);
        if (st.started() && !st.veteran()) {
            g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.startup.bonus.paid",
                    GuiText.compactUsd(st.bonusPaidCents())).getString(), leftW), x, ty, TechPanel.TEXT_MUTED, false);
            ty += line;
        }
        ty += 2;
        String grant = st.grantCents() > 0
                ? Component.translatable("data_center_addon.startup.grant.paid", GuiText.compactUsd(st.grantCents())).getString()
                : st.grantOfferCents() > 0 && !st.started()
                ? Component.translatable("data_center_addon.startup.grant.waiting", GuiText.compactUsd(st.grantOfferCents())).getString()
                : trs("grant.none");
        g.drawString(font, GuiText.fit(font, grant, leftW), x, ty, st.grantCents() > 0 ? TechPanel.GOOD : TechPanel.TEXT_MUTED, false);
        ty += line;
        g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.startup.fees",
                GuiText.compactUsd(st.feesPaidCents())).getString(), leftW), x, ty, TechPanel.TEXT_MUTED, false);
        ty += line;
        HostingSnapshot.Prepay prepay = st.prepay();
        if (prepay.available()) {
            int toggleW = 34;
            String state = prepay.enabled()
                    ? Component.translatable("data_center_addon.startup.prepay.state", GuiText.compactUsd(prepay.receivedCents()),
                    GuiText.compactUsd(prepay.openCents())).getString()
                    : trs("prepay.off");
            g.drawString(font, GuiText.fit(font, state, leftW - toggleW - 6), x, ty + 2, TechPanel.TEXT_MUTED, false);
            smallButton(g, x + leftW - toggleW, ty, toggleW, trs(prepay.enabled() ? "prepay.on_label" : "prepay.off_label"),
                    prepay.enabled() ? 0xFF2E6E8E : TechPanel.SLOT_BG, mouseX, mouseY,
                    () -> sendStartup(prepay.enabled() ? StartupService.Action.PREPAY_OFF : StartupService.Action.PREPAY_ON));
            if (inside(mouseX, mouseY, x, ty, leftW - toggleW - 6, ROW_H - 2)) {
                tooltip = List.of(Component.translatable("data_center_addon.startup.prepay"),
                        Component.translatable("data_center_addon.startup.prepay.hint", prepay.sharePercent(), prepay.discountPercent())
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.translatable("data_center_addon.startup.prepay.customers", prepay.customers())
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            ty += ROW_H;
        }
        ty += 6;

        // left: founding loan
        sectionTitle(g, x, ty, leftW, trs("loan"));
        ty += 13;
        int buttonW = 64;
        int textW = leftW - buttonW - 6;
        if (st.loanOfferCents() <= 0) {
            g.drawString(font, trs("loan.none"), x, ty, TechPanel.TEXT_MUTED, false);
        } else if (!st.loanTaken()) {
            wrapped(g, Component.translatable("data_center_addon.startup.loan.offer", GuiText.compactUsd(st.loanOfferCents()),
                    st.loanGraceDays(), GuiText.compactUsd(st.loanInstalmentCents())).getString(), x, ty, textW, TechPanel.TEXT, 2);
            boolean confirming = System.currentTimeMillis() < confirmLoanUntil;
            smallButton(g, x + leftW - buttonW, ty, buttonW, trs(confirming ? "loan.confirm" : "loan.take"),
                    confirming ? TechPanel.WARN : 0xFF2E6E8E, mouseX, mouseY, () -> {
                        if (System.currentTimeMillis() < confirmLoanUntil) {
                            confirmLoanUntil = 0;
                            sendStartup(StartupService.Action.TAKE_LOAN);
                        } else {
                            confirmLoanUntil = System.currentTimeMillis() + 3000;
                        }
                    });
        } else if (st.loanOutstandingCents() > 0) {
            g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.startup.loan.outstanding",
                    GuiText.compactUsd(st.loanOutstandingCents())).getString(), textW), x, ty, TechPanel.WARN, false);
            String terms = st.loanGraceDays() > 0
                    ? Component.translatable("data_center_addon.startup.loan.grace", st.loanGraceDays(),
                    GuiText.compactUsd(st.loanInstalmentCents())).getString()
                    : Component.translatable("data_center_addon.startup.loan.instalment", GuiText.compactUsd(st.loanInstalmentCents()))
                    .getString();
            g.drawString(font, GuiText.fit(font, terms, textW), x, ty + line, TechPanel.TEXT_MUTED, false);
            smallButton(g, x + leftW - buttonW, ty, buttonW, trs("loan.repay"), TechPanel.SLOT_BG, mouseX, mouseY,
                    () -> sendStartup(StartupService.Action.REPAY_LOAN));
        } else {
            g.drawString(font, trs("loan.repaid"), x, ty, TechPanel.GOOD, false);
        }

        // right: milestones
        g.fill(rightX - 6, y, rightX - 5, y + topH, HEADER_LINE);
        int reached = 0;
        for (Milestone m : Milestone.values()) {
            if (st.reached(m)) {
                reached++;
            }
        }
        sectionTitle(g, rightX, y, rightW, Component.translatable("data_center_addon.startup.milestones", reached,
                Milestone.values().length).getString());
        int my = y + 13;
        int rowH = 10;
        for (Milestone m : Milestone.values()) {
            if (my + rowH > y + topH) {
                break;
            }
            boolean done = st.reached(m);
            long reward = m.ordinal() < st.milestoneRewards().size() ? st.milestoneRewards().get(m.ordinal()) : 0;
            String rewardText = reward > 0 ? GuiText.compactUsd(reward) : "";
            int rewardW = font.width(rewardText);
            g.drawString(font, done ? "✔" : "○", rightX, my, done ? TechPanel.GOOD : TechPanel.TEXT_MUTED, false);
            g.drawString(font, GuiText.fit(font, Component.translatable(m.translationKey()).getString(), rightW - rewardW - 16),
                    rightX + 10, my, done ? TechPanel.TEXT_MUTED : TechPanel.TEXT, false);
            g.drawString(font, rewardText, rightX + rightW - rewardW, my, done ? TechPanel.TEXT_MUTED : TechPanel.GOOD, false);
            my += rowH;
        }

        // bottom: service tickets
        int by = y + h - ticketsH;
        g.fill(x - 4, by - 5, x + w + 4, by - 4, TechPanel.ACCENT);
        int[] col = columns(x, w, 0, 0.30, 0.62, 0.84);
        header(g, x, by, w, col, "column.ticket", "column.rack", "column.reward", "column.due");
        int ry = by + ROW_H + 2;
        if (st.tickets().isEmpty()) {
            g.drawString(font, GuiText.fit(font, trs("tickets.none"), w), x, ry + TEXT_DY, TechPanel.TEXT_MUTED, false);
        }
        for (int i = 0; i < st.tickets().size(); i++) {
            HostingSnapshot.Ticket t = st.tickets().get(i);
            stripe(g, x, ry, w, i);
            text(g, Component.translatable("data_center_addon.startup.ticket.kind." + t.kind()).getString(), col, 0, ry, TechPanel.TEXT);
            text(g, t.pos().getX() + ", " + t.pos().getY() + ", " + t.pos().getZ(), col, 1, ry, TechPanel.TEXT_MUTED);
            text(g, GuiText.compactUsd(t.rewardCents()), col, 2, ry, TechPanel.GOOD);
            text(g, t.daysLeft() + " " + trc("days_short"), col, 3, ry, t.daysLeft() <= 1 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
            if (inside(mouseX, mouseY, x, ry, w, ROW_H)) {
                tooltip = List.of(Component.translatable("data_center_addon.startup.ticket.kind." + t.kind()),
                        Component.translatable("data_center_addon.startup.tickets.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            ry += ROW_H;
        }
    }

    private void sectionTitle(GuiGraphics g, int x, int y, int w, String title) {
        g.drawString(font, GuiText.fit(font, title, w), x, y, TechPanel.ACCENT, false);
        g.fill(x, y + 10, x + w, y + 11, HEADER_LINE);
    }

    /** Text wrapped to {@code w}, at most {@code maxLines} lines; returns the y below it. */
    private int wrapped(GuiGraphics g, String text, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.drawString(font, lines.get(i), x, y, color, false);
            y += 11;
        }
        return y;
    }

    private static String percentBonus(double factor) {
        return "+" + Math.round(factor * 100) + " %";
    }

    private void sendStartup(StartupService.Action action) {
        PacketDistributor.sendToServer(new HostingManagerPayloads.StartupChange(menu.managerPos(), action));
    }

    private static String trs(String key) {
        return Component.translatable("data_center_addon.startup." + key).getString();
    }

    // ------------------------------------------------------------------ contracts

    /** The Contracts tab: a toggle between hosting reservations and delivery orders. */
    private void contracts(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        String reservations = Component.translatable("data_center_addon.delivery.view.reservations", s.activeContracts().size())
                .getString();
        String orders = Component.translatable("data_center_addon.delivery.view.deliveries", s.runningDeliveries().size())
                .getString();
        int bw = Math.max(font.width(reservations), font.width(orders)) + 14;
        toggle(g, x, y, bw, reservations, !deliveriesView, mouseX, mouseY, () -> deliveriesView = false);
        toggle(g, x + bw + 4, y, bw, orders, deliveriesView, mouseX, mouseY, () -> deliveriesView = true);
        int top = ROW_H + 4;
        if (deliveriesView) {
            deliveries(g, s, x, y + top, w, h - top, mouseX, mouseY);
        } else {
            reservations(g, s, x, y + top, w, h - top, mouseX, mouseY);
        }
    }

    private void toggle(GuiGraphics g, int x, int y, int w, String label, boolean active, int mouseX, int mouseY, Runnable action) {
        int h = ROW_H - 2;
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? HEADER_LINE : hover ? 0xFF2A3440 : TechPanel.SLOT_BG);
        if (active) {
            g.fill(x, y + h - 1, x + w, y + h, TechPanel.ACCENT);
        }
        g.drawCenteredString(font, label, x + w / 2, y + 3, active ? TechPanel.TEXT : TechPanel.TEXT_MUTED);
        buttons.add(new Button(x, y, w, h, () -> {
            action.run();
            scroll = 0;
        }));
    }

    // ------------------------------------------------------------------ deliveries (preview)

    private void deliveries(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<HostingSnapshot.Delivery> running = s.runningDeliveries();
        List<HostingSnapshot.Delivery> offers = s.deliveryOffers();
        if (running.isEmpty() && offers.isEmpty()) {
            message(g, x, y, w, h, Component.translatable("data_center_addon.delivery.none"));
            return;
        }
        HostingSnapshot.Delivery selected = null;
        for (HostingSnapshot.Delivery d : s.deliveries()) {
            if (d.id() == selectedDelivery) {
                selected = d;
            }
        }
        if (selected == null) {
            selected = running.isEmpty() ? offers.get(0) : running.get(0);
            selectedDelivery = selected.id();
        }

        int detailW = Math.max(150, (int) (w * 0.42));
        int listW = w - detailW - 8;
        int[] col = columns(x, listW, 0, 0.50, 0.68, 0.82);
        int ty = y;
        g.drawString(font, Component.translatable("data_center_addon.delivery.running", running.size(), DeliveryService.MAX_ACTIVE),
                x, ty + TEXT_DY, TechPanel.ACCENT, false);
        ty += ROW_H;
        g.fill(x, ty, x + listW, ty + 1, HEADER_LINE);
        ty += 2;
        if (running.isEmpty()) {
            g.drawString(font, trd("no_running"), x, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            ty += ROW_H;
        }
        for (int i = 0; i < running.size(); i++) {
            deliveryRow(g, running.get(i), x, ty, listW, col, i, mouseX, mouseY);
            ty += ROW_H;
        }
        ty += 6;
        g.drawString(font, trd("offers"), x, ty + TEXT_DY, TechPanel.ACCENT, false);
        ty += ROW_H;
        g.fill(x, ty, x + listW, ty + 1, HEADER_LINE);
        ty += 2;
        for (int i = 0; i < offers.size() && ty + ROW_H <= y + h - 10; i++) {
            deliveryRow(g, offers.get(i), x, ty, listW, col, i, mouseX, mouseY);
            ty += ROW_H;
        }
        g.drawString(font, GuiText.fit(font, trd("note"), listW), x, y + h - 9, TechPanel.TEXT_MUTED, false);

        deliveryDetail(g, selected, running.size() >= DeliveryService.MAX_ACTIVE, x + listW + 8, y, detailW, h, mouseX, mouseY);
    }

    private void deliveryRow(GuiGraphics g, HostingSnapshot.Delivery d, int x, int y, int w, int[] col, int row,
                             int mouseX, int mouseY) {
        boolean selected = d.id() == selectedDelivery;
        if (selected) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_SELECTED);
        } else if (inside(mouseX, mouseY, x, y, w, ROW_H)) {
            g.fill(x - 4, y, x + w + 4, y + ROW_H, ROW_HOVER);
        } else {
            stripe(g, x, y, w, row);
        }
        text(g, clientName(d.client()).getString(), col, 0, y, TechPanel.TEXT);
        text(g, d.offer() ? "—" : d.delivered() + "/" + d.total(), col, 1, y,
                !d.offer() && d.canDeliver() ? TechPanel.GOOD : TechPanel.TEXT_MUTED);
        boolean urgent = d.daysLeft() <= 1;
        text(g, d.daysLeft() + " " + trc("days_short"), col, 2, y, urgent ? TechPanel.WARN : TechPanel.TEXT_MUTED);
        int payW = x + w - col[3];
        String pay = GuiText.compactUsd(d.payCents());
        g.drawString(font, GuiText.fit(font, pay, payW), x + w - Math.min(payW, font.width(pay)), y + TEXT_DY, TechPanel.GOOD, false);
        buttons.add(new Button(x, y, w, ROW_H, () -> selectedDelivery = d.id()));
    }

    private void deliveryDetail(GuiGraphics g, HostingSnapshot.Delivery d, boolean full, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, 0x60000000);
        g.fill(x, y, x + 1, y + h, HEADER_LINE);
        int px = x + 6, pw = w - 12, ty = y + 4;
        g.drawString(font, GuiText.fit(font, clientName(d.client()).getString(), pw), px, ty, TechPanel.ACCENT, false);
        ty += 11;
        String status = d.offer()
                ? Component.translatable("data_center_addon.delivery.detail.offer", d.daysLeft(), d.days()).getString()
                : Component.translatable("data_center_addon.delivery.detail.running", d.daysLeft()).getString();
        g.drawString(font, GuiText.fit(font, status, pw), px, ty, d.daysLeft() <= 1 ? TechPanel.WARN : TechPanel.TEXT_MUTED, false);
        ty += 14;

        for (HostingSnapshot.Goal goal : d.goals()) {
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(goal.item()));
            g.fill(px, ty, px + 18, ty + 18, TechPanel.SLOT_BG);
            g.renderItem(icon, px + 1, ty + 1);
            String label = goal.count() + "× " + goalName(goal, icon).getString();
            g.drawString(font, GuiText.fit(font, label, pw - 24), px + 22, ty, TechPanel.TEXT, false);
            boolean done = goal.delivered() >= goal.count();
            String progress = d.offer()
                    ? Component.translatable("data_center_addon.delivery.detail.have", goal.inInventory()).getString()
                    : goal.delivered() + " / " + goal.count()
                    + (done ? " ✔" : goal.inInventory() > 0 ? "  (+" + Math.min(goal.inInventory(), goal.count() - goal.delivered()) + ")" : "");
            g.drawString(font, GuiText.fit(font, progress, pw - 24), px + 22, ty + 10,
                    done ? TechPanel.GOOD : goal.inInventory() > 0 ? TechPanel.ACCENT : TechPanel.TEXT_MUTED, false);
            if (inside(mouseX, mouseY, px, ty, 18, 18) && !icon.isEmpty()) {
                tooltip = List.of(goalName(goal, icon), Component.translatable("data_center_addon.delivery.goal.hint."
                        + goalKind(goal).name().toLowerCase(Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            ty += 22;
        }

        ty += 2;
        g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.delivery.detail.pay",
                GuiText.compactUsd(d.payCents()), d.reputation()).getString(), pw), px, ty, TechPanel.GOOD, false);
        ty += 11;
        g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.delivery.detail.fail", d.reputation())
                .getString(), pw), px, ty, TechPanel.DANGER, false);

        int by = y + h - ROW_H - 3;
        int half = (pw - 4) / 2;
        if (d.offer()) {
            if (full) {
                smallButton(g, px, by, half, trc("full"), 0xFF1A2028, mouseX, mouseY, null);
            } else {
                smallButton(g, px, by, half, trc("accept"), 0xFF2E6E8E, mouseX, mouseY,
                        () -> sendDelivery(d, DeliveryService.Action.ACCEPT));
            }
            smallButton(g, px + half + 4, by, half, trd("decline"), TechPanel.SLOT_BG, mouseX, mouseY,
                    () -> sendDelivery(d, DeliveryService.Action.DECLINE));
        } else {
            smallButton(g, px, by, half, trd("deliver"), d.canDeliver() ? 0xFF2E6E8E : TechPanel.SLOT_BG, mouseX, mouseY,
                    d.canDeliver() ? () -> sendDelivery(d, DeliveryService.Action.DELIVER) : null);
            boolean confirming = confirmCancel == -d.id() - 1 && System.currentTimeMillis() < confirmUntil;
            smallButton(g, px + half + 4, by, half, trc(confirming ? "confirm" : "cancel"),
                    confirming ? TechPanel.DANGER : TechPanel.SLOT_BG, mouseX, mouseY, () -> {
                        if (confirmCancel == -d.id() - 1 && System.currentTimeMillis() < confirmUntil) {
                            confirmCancel = -1;
                            sendDelivery(d, DeliveryService.Action.CANCEL);
                        } else {
                            confirmCancel = -d.id() - 1;
                            confirmUntil = System.currentTimeMillis() + 3000;
                        }
                    });
        }
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

    private void sendDelivery(HostingSnapshot.Delivery d, DeliveryService.Action action) {
        PacketDistributor.sendToServer(new HostingManagerPayloads.DeliveryChange(menu.managerPos(), d.id(), action));
    }

    private static String trd(String key) {
        return Component.translatable("data_center_addon.delivery." + key).getString();
    }

    // ------------------------------------------------------------------ reservations

    private void reservations(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<HostingSnapshot.Contract> active = s.activeContracts();
        List<HostingSnapshot.Contract> offers = s.offers();
        if (active.isEmpty() && offers.isEmpty()) {
            message(g, x, y, w, h, Component.translatable("data_center_addon.contract.none"));
            return;
        }
        int buttonW = 58;
        int[] col = columns(x, w - buttonW - 6, 0, 0.27, 0.53, 0.75, 0.88);
        int noteH = 20;
        int ty = y;

        // running contracts
        g.drawString(font, Component.translatable("data_center_addon.contract.active", active.size(), ContractService.MAX_ACTIVE),
                x, ty + TEXT_DY, TechPanel.ACCENT, false);
        ty += ROW_H;
        g.fill(x, ty, x + w, ty + 1, HEADER_LINE);
        ty += 2;
        if (active.isEmpty()) {
            g.drawString(font, trc("no_active"), x, ty + TEXT_DY, TechPanel.TEXT_MUTED, false);
            ty += ROW_H;
        }
        for (int i = 0; i < active.size(); i++) {
            HostingSnapshot.Contract c = active.get(i);
            if (inside(mouseX, mouseY, x, ty, w - buttonW - 6, ROW_H)) {
                g.fill(x - 4, ty, x + w + 4, ty + ROW_H, ROW_HOVER);
                tooltip = contractTooltip(c);
            } else {
                stripe(g, x, ty, w, i);
            }
            text(g, contractLabel(c), col, 0, ty, contractColor(c));
            text(g, c.accounts() + "× " + productName(c.product()).getString(), col, 1, ty, TechPanel.TEXT);
            coverage(g, c, col[2], ty, col[3] - col[2] - 8);
            text(g, c.daysDone() + " / " + c.days() + " " + trc("days_short") + "  " + strikes(c.strikes()), col, 3, ty,
                    c.strikes() > 0 ? TechPanel.WARN : TechPanel.TEXT_MUTED);
            text(g, GuiText.compactUsd(c.centsPerDay()), col, 4, ty, TechPanel.GOOD);
            boolean confirming = confirmCancel == c.id() && System.currentTimeMillis() < confirmUntil;
            smallButton(g, x + w - buttonW, ty + 1, buttonW, trc(confirming ? "confirm" : "cancel"),
                    confirming ? TechPanel.DANGER : TechPanel.SLOT_BG, mouseX, mouseY, () -> {
                        if (confirmCancel == c.id() && System.currentTimeMillis() < confirmUntil) {
                            confirmCancel = -1;
                            sendContract(c, ContractService.Action.CANCEL);
                        } else {
                            confirmCancel = c.id();
                            confirmUntil = System.currentTimeMillis() + 3000;
                        }
                    });
            ty += ROW_H;
        }

        // offers
        ty += 6;
        g.drawString(font, trc("offers"), x, ty + TEXT_DY, TechPanel.ACCENT, false);
        ty += ROW_H;
        g.fill(x, ty, x + w, ty + 1, HEADER_LINE);
        ty += 2;
        boolean full = active.size() >= ContractService.MAX_ACTIVE;
        for (int i = 0; i < offers.size() && ty + ROW_H <= y + h - noteH; i++) {
            HostingSnapshot.Contract c = offers.get(i);
            if (inside(mouseX, mouseY, x, ty, w - buttonW - 6, ROW_H)) {
                g.fill(x - 4, ty, x + w + 4, ty + ROW_H, ROW_HOVER);
                tooltip = contractTooltip(c);
            } else {
                stripe(g, x, ty, w, i);
            }
            text(g, contractLabel(c), col, 0, ty, contractColor(c));
            text(g, c.accounts() + "× " + productName(c.product()).getString(), col, 1, ty, TechPanel.TEXT);
            coverage(g, c, col[2], ty, col[3] - col[2] - 8);
            text(g, c.days() + " " + trc("days_short"), col, 3, ty, TechPanel.TEXT_MUTED);
            text(g, GuiText.compactUsd(c.centsPerDay()), col, 4, ty, TechPanel.GOOD);
            if (full) {
                smallButton(g, x + w - buttonW, ty + 1, buttonW, trc("full"), 0xFF1A2028, mouseX, mouseY, null);
            } else {
                smallButton(g, x + w - buttonW, ty + 1, buttonW, trc("accept"), TechPanel.SLOT_BG, mouseX, mouseY,
                        () -> sendContract(c, ContractService.Action.ACCEPT));
            }
            ty += ROW_H;
        }

        List<FormattedCharSequence> note = font.split(Component.translatable("data_center_addon.contract.note"), w);
        for (int i = 0; i < note.size() && i < 2; i++) {
            g.drawString(font, note.get(i), x, y + h - noteH + i * 10, TechPanel.TEXT_MUTED, false);
        }
    }

    /** "free / needed" with a bar: green when the free places cover the contracts, amber when they do not. */
    private void coverage(GuiGraphics g, HostingSnapshot.Contract c, int x, int y, int w) {
        float ratio = c.required() <= 0 ? 1 : (float) Math.min(1.0, c.free() / c.required());
        int barW = Math.max(16, w - 52);
        TechPanel.bar(g, x, y + 3, barW, 9, ratio, c.covered() ? TechPanel.GOOD : TechPanel.WARN);
        String label = GuiText.compactNumber((long) Math.floor(c.free())) + "/" + c.required();
        g.drawString(font, GuiText.fit(font, label, w - barW - 4), x + barW + 4, y + TEXT_DY,
                c.covered() ? TechPanel.TEXT_MUTED : TechPanel.WARN, false);
    }

    /** Client name with a mark for emergency and mini orders. */
    private static String contractLabel(HostingSnapshot.Contract c) {
        String name = clientName(c.client()).getString();
        return c.urgent() ? "⚡ " + name : c.mini() ? trc("mini") + " · " + name : name;
    }

    private static int contractColor(HostingSnapshot.Contract c) {
        return c.urgent() ? TechPanel.WARN : TechPanel.TEXT;
    }

    private List<Component> contractTooltip(HostingSnapshot.Contract c) {
        List<Component> lines = new ArrayList<>();
        lines.add(clientName(c.client()).copy().withStyle(net.minecraft.ChatFormatting.AQUA));
        if (c.urgent()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.urgent").withStyle(net.minecraft.ChatFormatting.GOLD));
        } else if (c.mini()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.mini").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("data_center_addon.contract.tip.wants", c.accounts(), productName(c.product()), c.days()));
        lines.add(Component.translatable("data_center_addon.contract.tip.free", GuiText.compactNumber((long) Math.floor(c.free())),
                c.required()).withStyle(c.covered() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD));
        lines.add(Component.translatable("data_center_addon.contract.tip.pay", GuiText.compactUsd(c.centsPerDay()),
                GuiText.compactUsd(c.bonusCents()), c.reputation()).withStyle(net.minecraft.ChatFormatting.GREEN));
        lines.add(Component.translatable("data_center_addon.contract.tip.penalty", GuiText.compactUsd(c.penaltyCents()),
                c.reputationLoss(), ContractService.MAX_STRIKES).withStyle(net.minecraft.ChatFormatting.RED));
        if (c.offer()) {
            lines.add(Component.translatable("data_center_addon.contract.tip.expires", c.expiresIn()).withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("data_center_addon.contract.tip.today", Math.round(c.metToday() * 100),
                    ContractService.DAY_SHARE_PERCENT).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private static String strikes(int strikes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ContractService.MAX_STRIKES; i++) {
            sb.append(i < strikes ? '●' : '○');
        }
        return sb.toString();
    }

    private static Component clientName(int client) {
        return Component.translatable("data_center_addon.contract.client." + client);
    }

    private void sendContract(HostingSnapshot.Contract c, ContractService.Action action) {
        PacketDistributor.sendToServer(new HostingManagerPayloads.ContractChange(menu.managerPos(), c.id(), action));
    }

    /** A small labelled button; {@code action == null} draws it disabled. */
    private void smallButton(GuiGraphics g, int x, int y, int w, String label, int background, int mouseX, int mouseY,
                             @Nullable Runnable action) {
        int h = ROW_H - 2;
        boolean hover = action != null && inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? TechPanel.ACCENT : background);
        g.drawCenteredString(font, GuiText.fit(font, label, w - 6), x + w / 2, y + 3,
                hover ? ON_ACCENT : action == null ? TechPanel.TEXT_MUTED : TechPanel.TEXT);
        if (action != null) {
            buttons.add(new Button(x, y, w, h, action));
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

    private static String trc(String key) {
        return Component.translatable("data_center_addon.contract." + key).getString();
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
