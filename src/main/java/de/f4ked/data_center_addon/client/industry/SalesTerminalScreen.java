package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.industry.Product;
import de.f4ked.data_center_addon.industry.SalesTerminalBlockEntity;
import de.f4ked.data_center_addon.industry.SalesTerminalMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Sales Terminal screen: on the left the price list (price now, the market's demand); on the right
 * the inbox and the player's inventory; the revenue at the top.
 */
public class SalesTerminalScreen extends TechPanelScreen<SalesTerminalMenu> {
    private static final int W = 478, H = 222;
    private static final int TABLE_X = 10, TABLE_W = 284, HEADER_Y = 34, ROW_Y = 47, ROW_H = 15;
    private static final int COL_PRICE = 140, COL_DEMAND = 196;

    private List<Component> tooltip;

    public SalesTerminalScreen(SalesTerminalMenu menu, Inventory inventory, Component title) {
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
        if (tooltip != null && (hoveredSlot == null || !hoveredSlot.hasItem())) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        tooltip = null;
        int x = leftPos, y = topPos;
        TechPanel.background(g, x, y, W, H);
        g.drawString(font, title, x + 10, y + 8, TechPanel.TEXT, true);
        String revenue = Component.translatable("data_center_addon.industry.sales.revenue", GuiText.compactUsd(menu.revenueCents()))
                .getString();
        g.drawString(font, revenue, x + 10, y + 20, TechPanel.GOOD, false);

        int tx = x + TABLE_X, hy = y + HEADER_Y;
        g.drawString(font, Component.translatable("data_center_addon.industry.column.product"), tx + 20, hy, TechPanel.ACCENT, false);
        g.drawString(font, Component.translatable("data_center_addon.industry.column.price"), tx + COL_PRICE, hy, TechPanel.ACCENT, false);
        g.drawString(font, Component.translatable("data_center_addon.industry.column.demand"), tx + COL_DEMAND, hy, TechPanel.ACCENT, false);
        g.fill(tx, hy + 10, tx + TABLE_W, hy + 11, 0x605AB0E0);
        Product[] all = Product.values();
        for (int i = 0; i < all.length; i++) {
            row(g, all[i], tx, y + ROW_Y + i * ROW_H, i, mouseX, mouseY);
        }
        g.fill(x + TABLE_X + TABLE_W + 5, hy, x + TABLE_X + TABLE_W + 6, y + H - 10, 0x605AB0E0);

        g.drawString(font, Component.translatable("data_center_addon.industry.sales.inbox"), x + SalesTerminalMenu.INV_X,
                y + SalesTerminalMenu.INBOX_Y - 11, TechPanel.ACCENT, false);
        g.drawString(font, playerInventoryTitle, x + SalesTerminalMenu.INV_X, y + SalesTerminalMenu.INV_Y - 11, TechPanel.ACCENT, false);
        for (Slot slot : menu.slots) {
            TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, slot.index < SalesTerminalBlockEntity.SLOTS ? TechPanel.INPUT
                    : TechPanel.SLOT_BG);
        }
        int hx = x + SalesTerminalMenu.INBOX_X + 3 * 18 + 8;
        int hyNote = y + SalesTerminalMenu.INBOX_Y + 4;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable("data_center_addon.industry.sales.note"), x + W - 10 - hx)) {
            g.drawString(font, line, hx, hyNote, TechPanel.TEXT_MUTED, false);
            hyNote += 10;
        }
    }

    /** One product: icon (half size), name, price now, demand bar. */
    private void row(GuiGraphics g, Product p, int x, int y, int index, int mouseX, int mouseY) {
        if (index % 2 == 1) {
            g.fill(x - 2, y - 2, x + TABLE_W + 2, y + ROW_H - 2, 0x14FFFFFF);
        }
        ItemStack stack = new ItemStack(p.item());
        g.pose().pushPose();
        g.pose().translate(x + 2, y - 1, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        g.drawString(font, GuiText.fit(font, stack.getHoverName().getString(), COL_PRICE - 26), x + 20, y + 2, TechPanel.TEXT, false);
        g.drawString(font, GuiText.compactUsd(menu.priceCents(p)), x + COL_PRICE, y + 2, TechPanel.GOOD, false);
        float demand = menu.demand(p);
        int bw = TABLE_W - COL_DEMAND - 34;
        TechPanel.bar(g, x + COL_DEMAND, y + 2, bw, 8, demand, demand > 0.8f ? TechPanel.GOOD : demand > 0.5f ? TechPanel.WARN
                : TechPanel.DANGER);
        g.drawString(font, Math.round(demand * 100) + "%", x + COL_DEMAND + bw + 4, y + 2, TechPanel.TEXT_MUTED, false);
        if (mouseX >= x && mouseX < x + TABLE_W && mouseY >= y - 2 && mouseY < y + ROW_H - 2) {
            tooltip = List.of(stack.getHoverName(),
                    Component.translatable("data_center_addon.industry.sales.price", GuiText.compactUsd(menu.priceCents(p)),
                            Math.round(demand * 100)).withStyle(ChatFormatting.GRAY),
                    Component.translatable("data_center_addon.industry.sales.demand.tip").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
