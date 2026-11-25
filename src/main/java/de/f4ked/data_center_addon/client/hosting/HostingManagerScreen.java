package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.hosting.HostingManagerMenu;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Hosting Manager screen, in Data Center's tech-panel look: a frame with seven tabs (overview,
 * customers, racks, contracts, startup, market, reputation). Each tab is its own class; {@link Panel} draws for them and
 * collects what can be clicked.
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
    private static final int TAB_W_MIN = 42;
    private static final int TAB_W_MAX = 64;
    private static final int TAB_H = 16;

    /** The tabs in order; the name gives the label key {@code data_center_addon.hosting.tab.<name>}. */
    private enum Tab {
        OVERVIEW, SEGMENTS, RACKS, CONTRACTS, STARTUP, MARKET, REPUTATION;

        /** Tabs that have something to show without racks on the network. */
        boolean worksWithoutRacks() {
            return this == CONTRACTS || this == STARTUP || this == MARKET || this == REPUTATION;
        }

        /** Tabs that work when no cable is plugged into the manager. */
        boolean worksWithoutNetwork() {
            return this == STARTUP || this == MARKET || this == REPUTATION;
        }
    }

    private final Panel panel = new Panel();
    private final Map<Tab, ManagerTab> tabs = new EnumMap<>(Tab.class);
    private Tab tab = Tab.OVERVIEW;

    public HostingManagerScreen(HostingManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
        tabs.put(Tab.OVERVIEW, new OverviewTab(panel));
        tabs.put(Tab.SEGMENTS, new CustomersTab(panel));
        tabs.put(Tab.RACKS, new RacksTab(panel, menu::managerPos));
        tabs.put(Tab.CONTRACTS, new ContractsTab(panel, menu::managerPos));
        tabs.put(Tab.STARTUP, new StartupTab(panel, menu::managerPos));
        tabs.put(Tab.MARKET, new MarketTab(panel, menu::managerPos));
        tabs.put(Tab.REPUTATION, new ReputationTab(panel, menu::managerPos));
    }

    @Override
    protected void init() {
        this.imageWidth = Mth.clamp(width - 2 * SCREEN_MARGIN, MIN_W, MAX_W);
        this.imageHeight = Mth.clamp(height - 2 * SCREEN_MARGIN, MIN_H, MAX_H);
        super.init();
        panel.font = font;
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // drawn in renderBg, in screen coordinates
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tooltip = panel.tooltip();
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        panel.begin(mouseX, mouseY);
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TechPanel.background(g, x, y, w, h);
        if (tabBar(g, x, y, w)) {
            g.drawString(font, title, x + PAD + 2, y + 10, TechPanel.TEXT, true);
        }

        int cx = x + PAD, cy = y + HEADER_H, cw = w - 2 * PAD, ch = h - HEADER_H - PAD;
        g.fill(cx, cy, cx + cw, cy + ch, 0x80000000);
        Component problem = problem();
        if (problem != null) {
            panel.message(g, cx, cy, cw, ch, problem);
            return;
        }
        tabs.get(tab).render(g, snapshot(), cx + 8, cy + 8, cw - 16, ch - 16);
    }

    /**
     * The tab buttons, right of the title; their width shrinks to fit narrow windows. When they do not fit
     * next to the title they take the whole width; returns whether the title fits.
     */
    private boolean tabBar(GuiGraphics g, int x, int y, int w) {
        Tab[] all = Tab.values();
        int beside = (w - 2 * PAD - font.width(title) - 16) / all.length - 3;
        boolean titleFits = beside >= TAB_W_MIN;
        int tabW = titleFits ? Math.min(beside, TAB_W_MAX) : Mth.clamp((w - 2 * PAD) / all.length - 3, 20, TAB_W_MAX);
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            int tx = x + w - PAD - (all.length - i) * (tabW + 3) + 3;
            int ty = y + 6;
            boolean active = t == tab;
            g.fill(tx, ty, tx + tabW, ty + TAB_H, active ? TechPanel.ACCENT : panel.hover(tx, ty, tabW, TAB_H) ? Panel.HOVER_BG : TechPanel.SLOT_BG);
            String label = Texts.hosting("tab." + t.name().toLowerCase(Locale.ROOT));
            g.drawCenteredString(font, GuiText.fit(font, label, tabW - 4), tx + tabW / 2, ty + 4, active ? Panel.ON_ACCENT : TechPanel.TEXT);
            panel.clickable(tx, ty, tabW, TAB_H, () -> {
                tab = t;
                panel.scroll = 0;
            });
        }
        return titleFits;
    }

    /** Why the current tab cannot be shown (no data yet, no cable, no racks), or null. */
    private Component problem() {
        HostingSnapshot s = snapshot();
        if (!HostingManagerClient.hasSnapshot(menu.managerPos())) {
            return Component.translatable("data_center_addon.hosting.loading");
        }
        if (!s.connected() && !tab.worksWithoutNetwork()) {
            return Component.translatable("data_center_addon.hosting.not_connected");
        }
        if (s.racks().isEmpty() && !tab.worksWithoutRacks()) {
            return s.foreignRacks() > 0
                    ? Component.translatable("data_center_addon.hosting.only_foreign", s.foreignRacks())
                    : Component.translatable("data_center_addon.hosting.no_racks");
        }
        return null;
    }

    private HostingSnapshot snapshot() {
        return HostingManagerClient.snapshotFor(menu.managerPos());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && panel.click(mouseX, mouseY)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            panel.scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
