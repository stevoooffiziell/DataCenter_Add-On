package de.f4ked.data_center_addon.client;

import com.example.datacenter.client.screen.TechPanel;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The hotbar in Data Center's tech-panel look: a dark panel with blue corner accents, a frame per slot,
 * the selected slot outlined in blue; the off-hand slot and the attack indicator (when set to the hotbar)
 * alike. Same size and place as Minecraft's hotbar, so hearts, food and experience bar stay where they are.
 * The spectator hotbar is left alone.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class TechHotbar {
    private static final int W = 182, H = 22, CELL = 20;
    private static final int BORDER = -13865368, SELECTED_FILL = 0x405AB0E0;

    private TechHotbar() {
    }

    @SubscribeEvent
    static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR) || !ClientConfig.techStyleHotbar()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || !(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        event.setCanceled(true);
        render(event.getGuiGraphics(), event.getPartialTick(), mc, player);
    }

    private static void render(GuiGraphics g, DeltaTracker delta, Minecraft mc, Player player) {
        int cx = g.guiWidth() / 2;
        int x = cx - W / 2, y = g.guiHeight() - H;
        g.pose().pushPose();
        g.pose().translate(0, 0, -90);
        panel(g, x, y, W, H);
        int selected = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            cell(g, x + 1 + i * CELL, y + 1, i == selected);
        }
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm side = player.getMainArm().getOpposite();
        int ox = side == HumanoidArm.LEFT ? x - 29 : x + W + 7;
        if (!offhand.isEmpty()) {
            panel(g, ox, y, H, H);
            cell(g, ox + 1, y + 1, false);
        }
        g.pose().popPose();

        int seed = 1;
        for (int i = 0; i < 9; i++) {
            slot(g, mc, delta, player, player.getInventory().items.get(i), x + 3 + i * CELL, y + 3, seed++);
        }
        if (!offhand.isEmpty()) {
            slot(g, mc, delta, player, offhand, ox + 3, y + 3, seed);
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float strength = player.getAttackStrengthScale(0);
            if (strength < 1) {
                int ax = side == HumanoidArm.RIGHT ? x - 12 : x + W + 6;
                if (!offhand.isEmpty()) {
                    ax += side == HumanoidArm.RIGHT ? -24 : 24;
                }
                int ah = H - 4;
                g.fill(ax, y + 2, ax + 6, y + 2 + ah, TechPanel.OUTLINE);
                int filled = (int) ((ah - 2) * strength);
                g.fill(ax + 1, y + 1 + ah - filled, ax + 5, y + 1 + ah, TechPanel.ACCENT);
            }
        }
    }

    /** The dark panel with border and blue corners, like Data Center's screens. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xE6131A22);
        g.fill(x, y, x + w, y + 1, BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + 1, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);
        int t = 4;
        g.fill(x, y, x + t, y + 1, TechPanel.ACCENT);
        g.fill(x, y, x + 1, y + t, TechPanel.ACCENT);
        g.fill(x + w - t, y, x + w, y + 1, TechPanel.ACCENT);
        g.fill(x + w - 1, y, x + w, y + t, TechPanel.ACCENT);
        g.fill(x, y + h - 1, x + t, y + h, TechPanel.ACCENT);
        g.fill(x, y + h - t, x + 1, y + h, TechPanel.ACCENT);
        g.fill(x + w - t, y + h - 1, x + w, y + h, TechPanel.ACCENT);
        g.fill(x + w - 1, y + h - t, x + w, y + h, TechPanel.ACCENT);
    }

    /** One 20 × 20 cell: a dark slot, outlined in blue when selected. */
    private static void cell(GuiGraphics g, int x, int y, boolean selected) {
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, TechPanel.OUTLINE);
        g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, selected ? SELECTED_FILL : TechPanel.SLOT_BG);
        if (selected) {
            g.fill(x, y, x + CELL, y + 1, TechPanel.ACCENT);
            g.fill(x, y + CELL - 1, x + CELL, y + CELL, TechPanel.ACCENT);
            g.fill(x, y, x + 1, y + CELL, TechPanel.ACCENT);
            g.fill(x + CELL - 1, y, x + CELL, y + CELL, TechPanel.ACCENT);
        }
    }

    /** An item with Minecraft's pick-up bounce, count and durability bar. */
    private static void slot(GuiGraphics g, Minecraft mc, DeltaTracker delta, Player player, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        float pop = stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false);
        if (pop > 0) {
            float f = 1 + pop / 5;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1 / f, (f + 1) / 2, 1);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) {
            g.pose().popPose();
        }
        g.renderItemDecorations(mc.font, stack, x, y);
    }
}
