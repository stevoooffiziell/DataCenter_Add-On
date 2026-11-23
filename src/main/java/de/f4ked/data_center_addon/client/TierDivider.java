package de.f4ked.data_center_addon.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Thin line in the tier colour under a tooltip's title, fading out towards both ends. */
public record TierDivider(PartTier tier, int width) implements TooltipComponent, ClientTooltipComponent {
    @Override
    public int getHeight() {
        return 4;
    }

    @Override
    public int getWidth(Font font) {
        return 0; // spans the tooltip, never widens it
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int half = width / 2;
        fade(graphics, x, y + 1, half, tier.text, true);
        fade(graphics, x + half, y + 1, width - half, tier.text, false);
    }

    /** A one pixel line that is transparent at its outer end ({@code fadeIn}: left end) and solid at the other. */
    static void fade(GuiGraphics graphics, int x, int y, int length, int color, boolean fadeIn) {
        if (length <= 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        for (int i = 0; i < length; i++) {
            float t = (i + 0.5f) / length;
            float alpha = fadeIn ? t : 1 - t;
            int a = Math.round(alpha * alpha * 0xE0);
            if (a > 0) {
                graphics.fill(x + i, y, x + i + 1, y + 1, a << 24 | rgb);
            }
        }
    }
}
