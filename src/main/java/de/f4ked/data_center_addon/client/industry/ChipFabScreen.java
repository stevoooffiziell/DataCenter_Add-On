package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabBlockEntity;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabMenu;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Locale;

/**
 * The chip production line's screen: power bar, three inputs → progress → two outputs, and below them the
 * state of the line: cleanroom walls, lithography units, speed, or what is missing.
 */
public class ChipFabScreen extends TechPanelScreen<ChipFabMenu> {
    private static final int W = 176, H = 178;

    private List<Component> tooltip;

    public ChipFabScreen(ChipFabMenu menu, Inventory inventory, Component title) {
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
        g.drawString(font, title, x + 8, y + 7, TechPanel.TEXT, true);

        int energy = menu.get(ChipFabBlockEntity.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(ChipFabBlockEntity.DATA_CAPACITY));
        int bx = x + 10, by = y + 20, bw = 10, bh = 56;
        g.fill(bx, by, bx + bw, by + bh, TechPanel.OUTLINE);
        int filled = Math.round((bh - 2) * Math.min(1f, (float) energy / capacity));
        g.fill(bx + 1, by + bh - 1 - filled, bx + bw - 1, by + bh - 1, TechPanel.WARN);
        if (hover(mouseX, mouseY, bx, by, bw, bh)) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.energy", energy, capacity),
                    Component.translatable("data_center_addon.industry.machine.draw", menu.get(ChipFabBlockEntity.DATA_FE_PER_TICK))
                            .withStyle(ChatFormatting.GRAY));
        }

        int progress = menu.get(ChipFabBlockEntity.DATA_PROGRESS);
        int total = menu.get(ChipFabBlockEntity.DATA_TOTAL);
        int ax = x + ChipFabMenu.IN_X + 3 * 18 + 4, ay = y + ChipFabMenu.IO_Y + 4;
        int aw = ChipFabMenu.OUT_X - ChipFabMenu.IN_X - 3 * 18 - 8;
        TechPanel.bar(g, ax, ay, aw, 8, total <= 0 ? 0 : (float) progress / total, TechPanel.ACCENT);
        if (hover(mouseX, mouseY, ax, ay - 2, aw, 12) && total > 0) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.progress", progress * 100 / total));
        }

        line(g, x + 28, y + 56, mouseX, mouseY);

        int qx = x + W - 18, qy = y + 5;
        boolean q = hover(mouseX, mouseY, qx, qy, 10, 10);
        g.fill(qx, qy, qx + 10, qy + 10, q ? TechPanel.ACCENT : TechPanel.SLOT_BG);
        g.drawCenteredString(font, "?", qx + 5, qy + 1, q ? 0xFF101418 : TechPanel.TEXT);
        if (q) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.recipes"),
                    Component.translatable("data_center_addon.industry.chip_fab.recipe").withStyle(ChatFormatting.GRAY),
                    Component.translatable("data_center_addon.industry.chip_fab.rules", ChipFabStructure.MIN_WALLS,
                            ChipFabStructure.WALLS_PER_UNIT, ChipFabStructure.MAX_UNITS).withStyle(ChatFormatting.DARK_GRAY));
        }

        for (Slot slot : menu.slots) {
            int accent = slot.index < ChipFabBlockEntity.INPUTS ? TechPanel.INPUT
                    : slot.index < ChipFabBlockEntity.SLOTS ? TechPanel.OUTPUT : TechPanel.SLOT_BG;
            TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, accent);
        }
    }

    /** "Cleanroom: 12 walls · 2 lithography units → speed ×2" or what is missing. */
    private void line(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int walls = menu.get(ChipFabBlockEntity.DATA_WALLS), units = menu.get(ChipFabBlockEntity.DATA_UNITS);
        int speed = menu.get(ChipFabBlockEntity.DATA_SPEED);
        ChipFabStructure.Status[] all = ChipFabStructure.Status.values();
        ChipFabStructure.Status status = all[Math.min(Math.max(0, menu.get(ChipFabBlockEntity.DATA_STATUS)), all.length - 1)];
        g.drawString(font, Component.translatable("data_center_addon.industry.chip_fab.structure", walls, units), x, y,
                TechPanel.TEXT_MUTED, false);
        Component state = status == ChipFabStructure.Status.FORMED
                ? Component.translatable("data_center_addon.industry.chip_fab.speed", speed)
                : Component.translatable("data_center_addon.industry.chip_fab.status." + status.name().toLowerCase(Locale.ROOT),
                ChipFabStructure.MIN_WALLS);
        g.drawString(font, state, x, y + 11, status == ChipFabStructure.Status.FORMED ? TechPanel.GOOD : TechPanel.WARN, false);
        if (hover(mouseX, mouseY, x, y - 1, 140, 22)) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.chip_fab.rules", ChipFabStructure.MIN_WALLS,
                    ChipFabStructure.WALLS_PER_UNIT, ChipFabStructure.MAX_UNITS).withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
