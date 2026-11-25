package de.f4ked.data_center_addon.client;

import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

import java.util.Set;

/**
 * Minecraft's own container screens in Data Center's tech-panel look: chests, barrels, ender chests and
 * shulker boxes, hoppers, dispensers and droppers, the crafting table, the furnaces and the survival
 * inventory.
 * <p>
 * The screens draw their normal background first; right after it ({@link ContainerScreenEvent.Render.Background})
 * the add-on paints the dark panel over it with a frame for every slot, and redraws what the background
 * held: the furnace's flame and progress, the crafting arrow, the player in the inventory. The labels
 * (dark grey on the vanilla texture) are drawn again in light colours on top
 * ({@link ContainerScreenEvent.Render.Foreground}). Screens of other mods are left alone.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class TechScreens {
    /** The screens that get the look (exact classes, so modded subclasses keep their own). */
    private static final Set<Class<?>> STYLED = Set.of(ContainerScreen.class, ShulkerBoxScreen.class, HopperScreen.class,
            DispenserScreen.class, CraftingScreen.class, FurnaceScreen.class, BlastFurnaceScreen.class, SmokerScreen.class,
            InventoryScreen.class);
    private static final int ARROW = 0xFF7E8B9A;

    private TechScreens() {
    }

    private static boolean styled(AbstractContainerScreen<?> screen) {
        return ClientConfig.techStyleScreens() && STYLED.contains(screen.getClass());
    }

    @SubscribeEvent
    static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!styled(screen)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft(), y = screen.getGuiTop(), w = screen.getXSize(), h = screen.getYSize();
        TechPanel.background(g, x, y, w, h);
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive()) {
                TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, slot.container instanceof net.minecraft.world.entity.player.Inventory
                        ? TechPanel.SLOT_BG : TechPanel.ACCENT);
            }
        }
        if (screen instanceof AbstractFurnaceScreen<?> furnace) {
            furnace(g, furnace.getMenu(), x, y);
        } else if (screen instanceof CraftingScreen) {
            arrow(g, x + 90, y + 35);
        } else if (screen instanceof InventoryScreen) {
            inventory(g, screen, x, y, event.getMouseX(), event.getMouseY());
        }
    }

    /** Flame (fuel left) as a bar rising from the bottom, the smelting progress as a bar. */
    private static void furnace(GuiGraphics g, AbstractFurnaceMenu menu, int x, int y) {
        int fx = x + 56, fy = y + 36;
        g.fill(fx, fy, fx + 14, fy + 14, TechPanel.OUTLINE);
        if (menu.isLit()) {
            int lit = Mth.ceil(menu.getLitProgress() * 12) + 1;
            g.fill(fx + 1, fy + 13 - lit, fx + 13, fy + 13, TechPanel.WARN);
        }
        TechPanel.bar(g, x + 79, y + 38, 24, 8, menu.getBurnProgress(), TechPanel.ACCENT);
    }

    /** The player in a dark frame and the arrow of the small crafting grid. */
    private static void inventory(GuiGraphics g, AbstractContainerScreen<?> screen, int x, int y, int mouseX, int mouseY) {
        g.fill(x + 25, y + 7, x + 76, y + 79, TechPanel.OUTLINE);
        g.fill(x + 26, y + 8, x + 75, y + 78, 0xFF0B0F14);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY,
                    mc.player);
        }
        arrow(g, x + 135, y + 29);
    }

    /** A plain arrow pointing right, about 22 × 15 like the vanilla one. */
    private static void arrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 6, x + 14, y + 9, ARROW);
        for (int i = 0; i < 7; i++) {
            g.fill(x + 14 + i, y + 1 + i, x + 15 + i, y + 14 - i, ARROW);
        }
    }

    @SubscribeEvent
    static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!styled(screen)) {
            return;
        }
        // the labels again, in the panel's colours, exactly over the dark grey ones (same glyphs, same place)
        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        g.drawString(font, screen.getTitle(), a.data_center_addon$titleLabelX(), a.data_center_addon$titleLabelY(),
                screen instanceof InventoryScreen ? TechPanel.ACCENT : TechPanel.TEXT, false);
        if (screen instanceof InventoryScreen) {
            // the inventory only shows its title ("Crafting")
            return;
        }
        g.drawString(font, a.data_center_addon$playerInventoryTitle(), a.data_center_addon$inventoryLabelX(),
                a.data_center_addon$inventoryLabelY(), TechPanel.ACCENT, false);
    }
}
