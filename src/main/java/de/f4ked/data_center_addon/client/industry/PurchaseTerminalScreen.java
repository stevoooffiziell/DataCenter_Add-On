package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.industry.Commodity;
import de.f4ked.data_center_addon.industry.PurchaseTerminalBlockEntity;
import de.f4ked.data_center_addon.industry.PurchaseTerminalBlockEntity.Action;
import de.f4ked.data_center_addon.industry.PurchaseTerminalMenu;
import de.f4ked.data_center_addon.network.ClientWallet;
import de.f4ked.data_center_addon.network.WalletSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Purchase Terminal screen: budget and its buttons at the top; on the left a table with one row per
 * commodity (price, buy one, buy a stack, auto-reorder target), on the right the terminal's stock and the
 * player's inventory.
 */
public class PurchaseTerminalScreen extends TechPanelScreen<PurchaseTerminalMenu> {
    private static final int W = 478, H = 222;
    private static final int TABLE_X = 10, TABLE_W = 284, HEADER_Y = 34, ROW_Y = 48, ROW_H = 21;
    /** Column offsets inside the table: name, price, buy buttons, auto-reorder. */
    private static final int COL_PRICE = 118, COL_BUY = 168, COL_AUTO = 222;
    private static final int BUTTON_BG = 0xFF26303A, BUTTON_HOVER = TechPanel.ACCENT;

    private record Button(int x, int y, int w, int h, int id) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private List<Component> tooltip;

    public PurchaseTerminalScreen(PurchaseTerminalMenu menu, Inventory inventory, Component title) {
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
        buttons.clear();
        tooltip = null;
        int x = leftPos, y = topPos;
        TechPanel.background(g, x, y, W, H);
        g.drawString(font, title, x + 10, y + 8, TechPanel.TEXT, true);
        budget(g, x + 10, y + 19, mouseX, mouseY);

        // table of commodities
        int tx = x + TABLE_X;
        header(g, tx, y + HEADER_Y);
        Commodity[] all = Commodity.values();
        for (int i = 0; i < all.length; i++) {
            row(g, all[i], tx, y + ROW_Y + i * ROW_H, i, mouseX, mouseY);
        }
        g.fill(x + TABLE_X + TABLE_W + 5, y + HEADER_Y, x + TABLE_X + TABLE_W + 6, y + H - 10, 0x605AB0E0);

        // stock and inventory on the right
        g.drawString(font, Component.translatable("data_center_addon.industry.stock"), x + PurchaseTerminalMenu.SLOTS_X,
                y + PurchaseTerminalMenu.SLOTS_Y - 11, TechPanel.ACCENT, false);
        g.drawString(font, playerInventoryTitle, x + PurchaseTerminalMenu.INV_X, y + PurchaseTerminalMenu.INV_Y - 11,
                TechPanel.ACCENT, false);
        for (Slot slot : menu.slots) {
            TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, slot.index < PurchaseTerminalBlockEntity.SLOTS ? TechPanel.OUTPUT
                    : TechPanel.SLOT_BG);
        }
    }

    /** "Budget: $1,234.56 · Wallet: $5,000" and the buttons. */
    private void budget(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        WalletSyncPayload wallet = ClientWallet.get();
        String text = Component.translatable("data_center_addon.industry.budget", GuiText.compactUsd(menu.budgetCents())).getString();
        g.drawString(font, text, x, y + 3, TechPanel.GOOD, false);
        int bx = x + font.width(text) + 8;
        bx = button(g, bx, y, Component.translatable("data_center_addon.industry.deposit", "$100").getString(),
                PurchaseTerminalMenu.button(Action.DEPOSIT_SMALL, 0), mouseX, mouseY, null);
        bx = button(g, bx, y, Component.translatable("data_center_addon.industry.deposit", "$1K").getString(),
                PurchaseTerminalMenu.button(Action.DEPOSIT_LARGE, 0), mouseX, mouseY, null);
        bx = button(g, bx, y, Component.translatable("data_center_addon.industry.withdraw").getString(),
                PurchaseTerminalMenu.button(Action.WITHDRAW_ALL, 0), mouseX, mouseY, null);
        Component fallback = Component.translatable(menu.walletFallback() ? "data_center_addon.industry.fallback.on"
                : "data_center_addon.industry.fallback.off");
        button(g, bx, y, fallback.getString(), PurchaseTerminalMenu.button(Action.TOGGLE_FALLBACK, 0), mouseX, mouseY,
                List.of(Component.translatable("data_center_addon.industry.fallback.tip").withStyle(ChatFormatting.GRAY)));
        if (wallet != null && mouseX >= x && mouseX < x + font.width(text) && mouseY >= y && mouseY < y + 12) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.wallet", GuiText.compactUsd(wallet.usdCents())),
                    Component.translatable("data_center_addon.industry.budget.tip").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Column titles with a line under them. */
    private void header(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("data_center_addon.industry.column.material"), x + 24, y, TechPanel.ACCENT, false);
        g.drawString(font, Component.translatable("data_center_addon.industry.column.price"), x + COL_PRICE, y, TechPanel.ACCENT, false);
        g.drawString(font, Component.translatable("data_center_addon.industry.column.buy"), x + COL_BUY, y, TechPanel.ACCENT, false);
        g.drawString(font, Component.translatable("data_center_addon.industry.auto"), x + COL_AUTO, y, TechPanel.ACCENT, false);
        g.fill(x, y + 10, x + TABLE_W, y + 11, 0x605AB0E0);
    }

    /** One commodity: icon, name, price per item, buy buttons and the auto-reorder target. */
    private void row(GuiGraphics g, Commodity c, int x, int y, int index, int mouseX, int mouseY) {
        if (index % 2 == 1) {
            g.fill(x - 2, y - 2, x + TABLE_W + 2, y + ROW_H - 2, 0x14FFFFFF);
        }
        ItemStack stack = new ItemStack(c.item());
        g.renderItem(stack, x + 2, y);
        int ty = y + 4;
        String name = Component.translatable(c.key()).getString();
        g.drawString(font, GuiText.fit(font, name, COL_PRICE - 30), x + 24, ty, TechPanel.TEXT, false);
        long price = menu.priceCents(c);
        g.drawString(font, GuiText.compactUsd(price), x + COL_PRICE, ty, TechPanel.TEXT_MUTED, false);
        if (mouseX >= x && mouseX < x + COL_BUY - 4 && mouseY >= y - 2 && mouseY < y + ROW_H - 2) {
            tooltip = List.of(Component.translatable(c.key()),
                    Component.translatable("data_center_addon.industry.price", GuiText.compactUsd(price)).withStyle(ChatFormatting.GRAY));
        }
        int stackSize = stack.getMaxStackSize();
        int bx = button(g, x + COL_BUY, ty - 2, "+1", PurchaseTerminalMenu.button(Action.BUY_ONE, c.ordinal()), mouseX, mouseY,
                List.of(Component.translatable("data_center_addon.industry.buy", 1, GuiText.compactUsd(price))));
        button(g, bx, ty - 2, "+" + stackSize, PurchaseTerminalMenu.button(Action.BUY_STACK, c.ordinal()), mouseX, mouseY,
                List.of(Component.translatable("data_center_addon.industry.buy", stackSize, GuiText.compactUsd(price * stackSize))));
        // auto-reorder: "‹ 64 ›"
        int target = menu.target(c);
        int ax = x + COL_AUTO;
        int after = button(g, ax, ty - 2, "‹", PurchaseTerminalMenu.button(Action.TARGET_DOWN, c.ordinal()), mouseX, mouseY, null);
        String value = target == 0 ? "–" : Integer.toString(target);
        g.drawCenteredString(font, value, after + 13, ty, target > 0 ? TechPanel.GOOD : TechPanel.TEXT_MUTED);
        button(g, after + 27, ty - 2, "›", PurchaseTerminalMenu.button(Action.TARGET_UP, c.ordinal()), mouseX, mouseY, null);
        if (mouseX >= ax && mouseX < x + TABLE_W && mouseY >= y - 2 && mouseY < y + ROW_H - 2 && tooltip == null) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.auto.tip", PurchaseTerminalBlockEntity.TARGET_STEP)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A small button; returns the x after it. */
    private int button(GuiGraphics g, int x, int y, String label, int id, int mouseX, int mouseY, List<Component> tip) {
        int w = font.width(label) + 10, h = 13;
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hovered ? BUTTON_HOVER : BUTTON_BG);
        g.drawCenteredString(font, label, x + w / 2, y + 3, hovered ? 0xFF101418 : TechPanel.TEXT);
        buttons.add(new Button(x, y, w, h, id));
        if (hovered && tip != null) {
            tooltip = tip;
        }
        return x + w + 3;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            for (Button b : List.copyOf(buttons)) {
                if (b.contains(mouseX, mouseY)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b.id());
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
