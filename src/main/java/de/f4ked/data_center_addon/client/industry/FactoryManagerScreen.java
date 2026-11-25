package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.industry.Commodity;
import de.f4ked.data_center_addon.industry.Product;
import de.f4ked.data_center_addon.industry.manager.FactoryMachine;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerMenu;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerService;
import de.f4ked.data_center_addon.industry.manager.FactorySnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Factory Manager screen, in Data Center's tech-panel look, with two tabs:
 * <ul>
 *   <li><b>Overview</b>: budget, spending, revenue and margin of the last 30 days and of today, and every
 *       factory machine around the manager with what it makes, its state and its power.</li>
 *   <li><b>Trade</b>: sales per product (units, revenue, price now, demand) and purchases per raw material.</li>
 * </ul>
 */
public class FactoryManagerScreen extends TechPanelScreen<FactoryManagerMenu> {
    private static final int W = 478, H = 250, PAD = 10, ROW_H = 14;
    private static final int LINE = 0x605AB0E0, ALT = 0x14FFFFFF;

    private enum Tab { OVERVIEW, TRADE }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<Component> tooltip;
    private Tab tab = Tab.OVERVIEW;
    private int scroll;

    public FactoryManagerScreen(FactoryManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // drawn in renderBg
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
        hits.clear();
        tooltip = null;
        int x = leftPos, y = topPos;
        TechPanel.background(g, x, y, W, H);
        g.drawString(font, title, x + PAD + 2, y + 10, TechPanel.TEXT, true);
        tabs(g, x, y, mouseX, mouseY);
        int cx = x + PAD, cy = y + 28, cw = W - 2 * PAD, ch = H - 28 - PAD;
        g.fill(cx, cy, cx + cw, cy + ch, 0x80000000);
        if (!FactoryManagerClient.has(menu.managerPos())) {
            g.drawCenteredString(font, Component.translatable("data_center_addon.hosting.loading"), cx + cw / 2, cy + ch / 2, TechPanel.TEXT_MUTED);
            return;
        }
        FactorySnapshot s = FactoryManagerClient.snapshot(menu.managerPos());
        if (tab == Tab.OVERVIEW) {
            overview(g, s, cx + 8, cy + 8, cw - 16, ch - 16, mouseX, mouseY);
        } else {
            trade(g, s, cx + 8, cy + 8, cw - 16, ch - 16, mouseX, mouseY);
        }
    }

    private void tabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Tab[] all = Tab.values();
        int tw = 70;
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            int tx = x + W - PAD - (all.length - i) * (tw + 3) + 3, ty = y + 6;
            boolean active = t == tab;
            boolean hover = hover(mouseX, mouseY, tx, ty, tw, 16);
            g.fill(tx, ty, tx + tw, ty + 16, active ? TechPanel.ACCENT : hover ? 0xFF2A3440 : TechPanel.SLOT_BG);
            g.drawCenteredString(font, Component.translatable("data_center_addon.industry.manager.tab." + t.name().toLowerCase(Locale.ROOT)),
                    tx + tw / 2, ty + 4, active ? 0xFF101418 : TechPanel.TEXT);
            hits.add(new Hit(tx, ty, tw, 16, () -> {
                tab = t;
                scroll = 0;
            }));
        }
    }

    // ------------------------------------------------------------------ overview

    private void overview(GuiGraphics g, FactorySnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        String[][] cards = {
                {key("card.budget"), GuiText.compactUsd(s.budgetCents())},
                {key("card.spent"), GuiText.compactUsd(s.spent30())},
                {key("card.revenue"), GuiText.compactUsd(s.revenue30())},
                {key("card.margin"), (s.margin30() < 0 ? "−" : "") + GuiText.compactUsd(Math.abs(s.margin30()))},
                {key("card.today"), GuiText.compactUsd(s.revenueToday() - s.spentToday())},
        };
        int gap = 6, cardW = (w - (cards.length - 1) * gap) / cards.length, cardH = 34;
        for (int i = 0; i < cards.length; i++) {
            int bx = x + i * (cardW + gap);
            g.fill(bx, y, bx + cardW, y + cardH, TechPanel.SLOT_BG);
            g.fill(bx, y, bx + 2, y + cardH, TechPanel.ACCENT);
            g.drawString(font, GuiText.fit(font, cards[i][0], cardW - 12), bx + 8, y + 6, TechPanel.TEXT_MUTED, false);
            int color = i == 3 ? (s.margin30() >= 0 ? TechPanel.GOOD : TechPanel.DANGER) : i == 1 ? TechPanel.WARN : TechPanel.GOOD;
            g.drawString(font, cards[i][1], bx + 8, y + 19, color, false);
            if (hover(mouseX, mouseY, bx, y, cardW, cardH)) {
                tooltip = List.of(Component.translatable("data_center_addon.industry.manager.card." + new String[]{"budget", "spent",
                        "revenue", "margin", "today"}[i] + ".tip", s.purchaseTerminals(), s.salesTerminals(),
                        GuiText.compactUsd(s.revenueToday()), GuiText.compactUsd(s.spentToday())).withStyle(ChatFormatting.GRAY));
            }
        }
        int ty = y + cardH + 10;
        g.drawString(font, Component.translatable("data_center_addon.industry.manager.machines", s.machines().size(),
                FactoryManagerService.RADIUS), x, ty, TechPanel.ACCENT, false);
        ty += 12;
        int[] col = {x, x + (int) (w * 0.30), x + (int) (w * 0.58), x + (int) (w * 0.74), x + (int) (w * 0.88)};
        header(g, x, ty, w, col, "column.machine", "column.makes", "column.state", "column.power", "column.position");
        ty += ROW_H + 2;
        int visible = (y + h - ty) / ROW_H;
        if (s.machines().isEmpty()) {
            g.drawString(font, Component.translatable("data_center_addon.industry.manager.no_machines"), x + 4, ty + 4, TechPanel.TEXT_MUTED, false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, s.machines().size() - visible)));
        FactoryMachine.State[] states = FactoryMachine.State.values();
        for (int i = scroll; i < s.machines().size() && i < scroll + visible; i++) {
            FactorySnapshot.Machine m = s.machines().get(i);
            if (i % 2 == 1) {
                g.fill(x - 2, ty, x + w + 2, ty + ROW_H, ALT);
            }
            String name = BuiltInRegistries.BLOCK.get(m.block()).getName().getString();
            g.drawString(font, GuiText.fit(font, name, col[1] - col[0] - 6), col[0], ty + 3, TechPanel.TEXT, false);
            ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(m.output()));
            if (!out.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(col[1], ty, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(out, 0, 0);
                g.pose().popPose();
                g.drawString(font, GuiText.fit(font, out.getHoverName().getString(), col[2] - col[1] - 20), col[1] + 15, ty + 3,
                        TechPanel.TEXT_MUTED, false);
            } else {
                g.drawString(font, "–", col[1], ty + 3, TechPanel.TEXT_MUTED, false);
            }
            FactoryMachine.State state = states[Math.max(0, Math.min(states.length - 1, m.state()))];
            int color = switch (state) {
                case WORKING -> TechPanel.GOOD;
                case IDLE -> TechPanel.TEXT_MUTED;
                case NO_POWER -> TechPanel.DANGER;
                case BLOCKED -> TechPanel.WARN;
            };
            g.drawString(font, GuiText.fit(font, Component.translatable("data_center_addon.industry.manager.state."
                    + state.name().toLowerCase(Locale.ROOT)).getString(), col[3] - col[2] - 6), col[2], ty + 3, color, false);
            TechPanel.bar(g, col[3], ty + 3, col[4] - col[3] - 10, 8, m.energy(), m.energy() < 0.1f ? TechPanel.DANGER : TechPanel.WARN);
            g.drawString(font, m.pos().getX() + ", " + m.pos().getY() + ", " + m.pos().getZ(), col[4], ty + 3, TechPanel.TEXT_MUTED, false);
            ty += ROW_H;
        }
    }

    // ------------------------------------------------------------------ trade

    private void trade(GuiGraphics g, FactorySnapshot s, int x, int y, int w, int h, int mouseX, int mouseY) {
        int leftW = (int) (w * 0.6);
        g.drawString(font, Component.translatable("data_center_addon.industry.manager.sales"), x, y, TechPanel.ACCENT, false);
        int[] sc = {x, x + (int) (leftW * 0.42), x + (int) (leftW * 0.56), x + (int) (leftW * 0.74), x + (int) (leftW * 0.88)};
        header(g, x, y + 12, leftW, sc, "column.product", "column.units", "column.revenue", "column.price_now", "column.demand");
        int ty = y + 12 + ROW_H + 2;
        for (int i = 0; i < s.sales().size(); i++) {
            FactorySnapshot.Sale sale = s.sales().get(i);
            Product[] all = Product.values();
            Product p = all[Math.min(sale.product(), all.length - 1)];
            if (i % 2 == 1) {
                g.fill(x - 2, ty, x + leftW + 2, ty + ROW_H, ALT);
            }
            g.drawString(font, GuiText.fit(font, new ItemStack(p.item()).getHoverName().getString(), sc[1] - sc[0] - 6), sc[0], ty + 3,
                    TechPanel.TEXT, false);
            g.drawString(font, GuiText.compactNumber(sale.units()), sc[1], ty + 3, TechPanel.TEXT_MUTED, false);
            g.drawString(font, GuiText.compactUsd(sale.cents()), sc[2], ty + 3, TechPanel.GOOD, false);
            g.drawString(font, GuiText.compactUsd(sale.priceNow()), sc[3], ty + 3, TechPanel.TEXT, false);
            int demand = sale.demand() / 10;
            g.drawString(font, demand + "%", sc[4], ty + 3, demand > 80 ? TechPanel.GOOD : demand > 50 ? TechPanel.WARN : TechPanel.DANGER, false);
            ty += ROW_H;
        }
        int rx = x + leftW + 12, rw = w - leftW - 12;
        g.fill(rx - 6, y, rx - 5, y + h, LINE);
        g.drawString(font, Component.translatable("data_center_addon.industry.manager.purchases"), rx, y, TechPanel.ACCENT, false);
        int[] pc = {rx, rx + (int) (rw * 0.44), rx + (int) (rw * 0.62), rx + (int) (rw * 0.82)};
        header(g, rx, y + 12, rw, pc, "column.material", "column.units", "column.spent", "column.price_now");
        ty = y + 12 + ROW_H + 2;
        for (int i = 0; i < s.purchases().size(); i++) {
            FactorySnapshot.Purchase buy = s.purchases().get(i);
            Commodity[] all = Commodity.values();
            Commodity c = all[Math.min(buy.commodity(), all.length - 1)];
            if (i % 2 == 1) {
                g.fill(rx - 2, ty, rx + rw + 2, ty + ROW_H, ALT);
            }
            g.drawString(font, GuiText.fit(font, Component.translatable(c.key()).getString(), pc[1] - pc[0] - 6), pc[0], ty + 3,
                    TechPanel.TEXT, false);
            g.drawString(font, GuiText.compactNumber(buy.units()), pc[1], ty + 3, TechPanel.TEXT_MUTED, false);
            g.drawString(font, GuiText.compactUsd(buy.cents()), pc[2], ty + 3, TechPanel.WARN, false);
            g.drawString(font, GuiText.compactUsd(buy.priceNow()), pc[3], ty + 3, TechPanel.TEXT, false);
            ty += ROW_H;
        }
        g.drawString(font, Component.translatable("data_center_addon.industry.manager.trade.note"), x, y + h - 9, TechPanel.TEXT_MUTED, false);
    }

    // ------------------------------------------------------------------ helpers

    private void header(GuiGraphics g, int x, int y, int w, int[] col, String... keys) {
        for (int i = 0; i < keys.length && i < col.length; i++) {
            int maxW = (i + 1 < col.length ? col[i + 1] : x + w) - col[i] - 4;
            g.drawString(font, GuiText.fit(font, key(keys[i]), maxW), col[i], y + 3, TechPanel.ACCENT, false);
        }
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, LINE);
    }

    private static String key(String k) {
        return Component.translatable("data_center_addon.industry.manager." + k).getString();
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
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
