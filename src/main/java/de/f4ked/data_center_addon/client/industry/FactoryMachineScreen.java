package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlockEntity;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineMenu;
import de.f4ked.data_center_addon.industry.machine.MachineRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory machine's screen: power bar on the left, inputs → progress → outputs, the player's
 * inventory below. Hovering the "?" lists the machine's recipes.
 */
public class FactoryMachineScreen extends TechPanelScreen<FactoryMachineMenu> {
    private static final int W = 176, H = 166;

    private List<Component> tooltip;

    public FactoryMachineScreen(FactoryMachineMenu menu, Inventory inventory, Component title) {
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

        // power: vertical bar
        int energy = menu.get(FactoryMachineBlockEntity.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(FactoryMachineBlockEntity.DATA_CAPACITY));
        int bx = x + 10, by = y + 20, bw = 10, bh = 52;
        g.fill(bx, by, bx + bw, by + bh, TechPanel.OUTLINE);
        int filled = Math.round((bh - 2) * Math.min(1f, (float) energy / capacity));
        g.fill(bx + 1, by + bh - 1 - filled, bx + bw - 1, by + bh - 1, TechPanel.WARN);
        if (hover(mouseX, mouseY, bx, by, bw, bh)) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.energy", energy, capacity),
                    Component.translatable("data_center_addon.industry.machine.draw",
                            menu.get(FactoryMachineBlockEntity.DATA_FE_PER_TICK)).withStyle(ChatFormatting.GRAY));
        }

        // progress arrow between inputs and outputs
        int progress = menu.get(FactoryMachineBlockEntity.DATA_PROGRESS);
        int total = menu.get(FactoryMachineBlockEntity.DATA_TOTAL);
        int ax = x + FactoryMachineMenu.IN_X + 2 * 18 + 4, ay = y + FactoryMachineMenu.IO_Y + 4;
        int aw = FactoryMachineMenu.OUT_X - FactoryMachineMenu.IN_X - 2 * 18 - 8;
        TechPanel.bar(g, ax, ay, aw, 8, total <= 0 ? 0 : (float) progress / total, TechPanel.ACCENT);
        if (hover(mouseX, mouseY, ax, ay - 2, aw, 12) && total > 0) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.progress", progress * 100 / total));
        }

        // recipes
        int qx = x + W - 18, qy = y + 5;
        boolean q = hover(mouseX, mouseY, qx, qy, 10, 10);
        g.fill(qx, qy, qx + 10, qy + 10, q ? TechPanel.ACCENT : TechPanel.SLOT_BG);
        g.drawCenteredString(font, "?", qx + 5, qy + 1, q ? 0xFF101418 : TechPanel.TEXT);
        if (q) {
            tooltip = recipes();
        }

        for (Slot slot : menu.slots) {
            int accent = slot.index < FactoryMachineBlockEntity.INPUTS ? TechPanel.INPUT
                    : slot.index < FactoryMachineBlockEntity.SLOTS ? TechPanel.OUTPUT : TechPanel.SLOT_BG;
            TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, accent);
        }
    }

    /** "2 × Quartz + Coal → Silicon (6 s, 40 FE/t)" per recipe. */
    private List<Component> recipes() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("data_center_addon.industry.machine.recipes"));
        for (MachineRecipe r : menu.kind().recipes()) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < r.inputs().size(); i++) {
                MachineRecipe.Input in = r.inputs().get(i);
                if (i > 0) {
                    line.append(" + ");
                }
                if (in.count() > 1) {
                    line.append(in.count() + " × ");
                }
                line.append(in.item().get().getDescription());
            }
            line.append(" → ");
            if (r.count() > 1) {
                line.append(r.count() + " × ");
            }
            line.append(r.output().get().getDescription());
            line.append(Component.literal(String.format(java.util.Locale.ROOT, "  (%.1f s, %d FE/t)", r.ticks() / 20.0, r.fePerTick()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(line.withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
