package de.f4ked.data_center_addon.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Tooltip block listing a server's built-in parts:
 * <pre>
 *   ───────── Built-in Components ─────────
 *   ▌[icon] CPU        2× EPYC 7763
 *   ▌[icon] RAM        8× 64 GB DDR4
 * </pre>
 * The bar and the part name are coloured by the part's tier; the slot label by slot type.
 */
public record ServerPartsTooltip(ServerParts parts) implements TooltipComponent, ClientTooltipComponent {
    private static final int TOP = 13;
    private static final int ROW = 10;
    private static final int ICON_X = 4;
    private static final int LABEL_X = 15;
    private static final int GAP = 6;
    private static final int HEADER = 0xFFB8B8B8;
    private static final int LABEL_DIM = 0xFF9A9A9A;
    private static final int COUNT = 0xFF8C8C8C;
    private static final int UNRATED = 0xFFE0E0E0;
    private static final int DEFECTIVE = 0xFFFF5555;
    private static final int EMPTY = 0xFF7A7A7A;

    private int lines() {
        return Math.max(1, parts.rows().size());
    }

    @Override
    public int getHeight() {
        return TOP + lines() * ROW + 2;
    }

    @Override
    public int getWidth(Font font) {
        int header = font.width(header()) + 2 * 12;
        if (parts.rows().isEmpty()) {
            return Math.max(header, font.width(empty()));
        }
        return Math.max(header, LABEL_X + labelWidth(font) + GAP + valueWidth(font));
    }

    private int labelWidth(Font font) {
        int width = 0;
        for (ServerParts.Row row : parts.rows()) {
            width = Math.max(width, font.width(row.category().label()));
        }
        return width;
    }

    private int valueWidth(Font font) {
        int width = 0;
        for (ServerParts.Row row : parts.rows()) {
            width = Math.max(width, font.width(count(row)) + font.width(name(row)));
        }
        return width;
    }

    private static Component header() {
        return Component.translatable("data_center_addon.tooltip.server.components");
    }

    private static Component empty() {
        return Component.translatable("data_center_addon.tooltip.server.empty");
    }

    private static String count(ServerParts.Row row) {
        return row.count() > 1 ? row.count() + "× " : "";
    }

    private static String name(ServerParts.Row row) {
        return row.defective() ? "✖ " + row.name() : row.name();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int width = getWidth(font);
        int accent = parts.tier() == null ? HEADER : parts.tier().text;

        // header: "──── Built-in Components ────" centred over the block
        Component header = header();
        int headerWidth = font.width(header);
        int headerX = x + (width - headerWidth) / 2;
        int lineY = y + 6;
        TierDivider.fade(graphics, x, lineY, headerX - 4 - x, accent, true);
        TierDivider.fade(graphics, headerX + headerWidth + 4, lineY, x + width - (headerX + headerWidth + 4), accent, false);
        graphics.drawString(font, header, headerX, y + 2, HEADER, true);

        int rowY = y + TOP;
        if (parts.rows().isEmpty()) {
            graphics.drawString(font, empty(), x, rowY, EMPTY, true);
            return;
        }
        int valueX = x + LABEL_X + labelWidth(font) + GAP;
        for (ServerParts.Row row : parts.rows()) {
            int tierColor = row.tier() == null ? UNRATED : row.tier().text;
            // tier bar
            graphics.fill(x, rowY - 1, x + 2, rowY + 8, row.tier() == null ? 0x40FFFFFF : tierColor);
            // half-size icon
            graphics.pose().pushPose();
            graphics.pose().translate(x + ICON_X, rowY - 0.5f, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.renderItem(row.icon(), 0, 0);
            graphics.pose().popPose();
            // slot label, count, part name
            graphics.drawString(font, row.category().label(), x + LABEL_X, rowY, blend(row.category().color, LABEL_DIM), true);
            String count = count(row);
            graphics.drawString(font, count, valueX, rowY, COUNT, true);
            graphics.drawString(font, name(row), valueX + font.width(count), rowY,
                    row.defective() ? DEFECTIVE : tierColor, true);
            rowY += ROW;
        }
    }

    /** Mixes the slot accent with grey so labels stay quieter than the part names. */
    private static int blend(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
