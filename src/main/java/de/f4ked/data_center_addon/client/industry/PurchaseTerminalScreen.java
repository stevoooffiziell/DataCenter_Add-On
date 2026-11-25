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
 * The Purchase Terminal screen: budget and its buttons at the top, a card per commodity (price, buy one,
 * buy a stack, auto-reorder target), the terminal's stock and the player's inventory below.
 */
public class PurchaseTerminalScreen extends TechPanelScreen<PurchaseTerminalMenu> {
    private static final int W = 330, H = 282;
    private static final int CARD_Y = 30, CARD_H = 26, CARD_GAP = 2;
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
        g.drawString(font, title, x + 10, y + 7, TechPanel.TEXT, true);
        budget(g, x + 10, y + 17, mouseX, mouseY);
        Commodity[] all = Commodity.values();
        int cardW = (W - 20 - 6) / 2;
        for (int i = 0; i < all.length; i++) {
            int cx = x + 10 + (i % 2) * (cardW + 6);
            int cy = y + CARD_Y + (i / 2) * (CARD_H + CARD_GAP);
            card(g, all[i], cx, cy, cardW, mouseX, mouseY);
        }
        g.drawString(font, Component.translatable("data_center_addon.industry.stock"), x + PurchaseTerminalMenu.SLOTS_X,
                y + PurchaseTerminalMenu.SLOTS_Y - 10, TechPanel.TEXT_MUTED, false);
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

    /** One commodity: icon, name, price, buy buttons and the auto-reorder target. */
    private void card(GuiGraphics g, Commodity c, int x, int y, int w, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + CARD_H, TechPanel.SLOT_BG);
        g.fill(x, y, x + 2, y + CARD_H, TechPanel.ACCENT);
        ItemStack stack = new ItemStack(c.item());
        g.renderItem(stack, x + 5, y + 5);
        String name = Component.translatable(c.key()).getString();
        g.drawString(font, GuiText.fit(font, name, w - 90), x + 24, y + 3, TechPanel.TEXT, false);
        long price = menu.priceCents(c);
        String priceText = Component.translatable("data_center_addon.industry.price", GuiText.compactUsd(price)).getString();
        g.drawString(font, priceText, x + 24, y + 14, TechPanel.TEXT_MUTED, false);
        int stackSize = stack.getMaxStackSize();
        int bx = x + w - 86;
        bx = button(g, bx, y + 2, "+1", PurchaseTerminalMenu.button(Action.BUY_ONE, c.ordinal()), mouseX, mouseY,
                List.of(Component.translatable("data_center_addon.industry.buy", 1, GuiText.compactUsd(price))));
        button(g, bx, y + 2, "+" + stackSize, PurchaseTerminalMenu.button(Action.BUY_STACK, c.ordinal()), mouseX, mouseY,
                List.of(Component.translatable("data_center_addon.industry.buy", stackSize, GuiText.compactUsd(price * stackSize))));
        // auto-reorder: "Auto ‹ 64 ›"
        int target = menu.target(c);
        int ty = y + 14;
        int ax = x + w - 86;
        g.drawString(font, Component.translatable("data_center_addon.industry.auto"), ax, ty, TechPanel.TEXT_MUTED, false);
        int sx = ax + 30;
        sx = button(g, sx, ty - 2, "‹", PurchaseTerminalMenu.button(Action.TARGET_DOWN, c.ordinal()), mouseX, mouseY, null);
        String value = target == 0 ? "–" : Integer.toString(target);
        g.drawCenteredString(font, value, sx + 12, ty, target > 0 ? TechPanel.GOOD : TechPanel.TEXT_MUTED);
        button(g, sx + 24, ty - 2, "›", PurchaseTerminalMenu.button(Action.TARGET_UP, c.ordinal()), mouseX, mouseY, null);
        if (mouseX >= ax && mouseX < x + w && mouseY >= ty - 2 && mouseY < ty + 10 && tooltip == null) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.auto.tip", PurchaseTerminalBlockEntity.TARGET_STEP)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A small button; returns the x after it. */
    private int button(GuiGraphics g, int x, int y, String label, int id, int mouseX, int mouseY, List<Component> tip) {
        int w = font.width(label) + 8, h = 11;
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hovered ? BUTTON_HOVER : BUTTON_BG);
        g.drawCenteredString(font, label, x + w / 2, y + 2, hovered ? 0xFF101418 : TechPanel.TEXT);
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
