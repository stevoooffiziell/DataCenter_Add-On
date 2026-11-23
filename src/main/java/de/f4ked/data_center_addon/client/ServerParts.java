package de.f4ked.data_center_addon.client;

import com.example.datacenter.component.ComponentCondition;
import com.example.datacenter.server.ServerLayout;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * The parts built into a Data Center server item, grouped by slot type and identical model.
 */
public record ServerParts(List<Row> rows, @Nullable PartTier tier) {

    public enum Category {
        MAINBOARD(0xFF8BC34A, 1, i -> ServerLayout.SLOT_MAINBOARD),
        CPU(0xFF64B5F6, ServerLayout.MAX_CPU, ServerLayout::cpuSlot),
        GPU(0xFFE57373, ServerLayout.MAX_GPU, ServerLayout::gpuSlot),
        RAM(0xFF4DD0E1, ServerLayout.MAX_RAM, ServerLayout::ramSlot),
        STORAGE(0xFFFFB74D, ServerLayout.MAX_STORAGE, ServerLayout::storageSlot),
        PSU(0xFFFFD54F, ServerLayout.MAX_PSU, ServerLayout::psuSlot),
        FAN(0xFF90A4AE, ServerLayout.MAX_FAN, ServerLayout::fanSlot),
        RISER(0xFFA1887F, ServerLayout.MAX_RISER, ServerLayout::riserSlot);

        /** Accent colour of the slot label. */
        final int color;
        final int slots;
        final IntUnaryOperator slot;

        Category(int color, int slots, IntUnaryOperator slot) {
            this.color = color;
            this.slots = slots;
            this.slot = slot;
        }

        Component label() {
            return Component.translatable("data_center_addon.tooltip.server." + name().toLowerCase(Locale.ROOT));
        }

        /** Processors count double for the server's overall tier. */
        int weight() {
            return this == CPU || this == GPU ? 2 : 1;
        }
    }

    /** One model of part in one slot type; {@code count} identical parts collapse into one row. */
    public record Row(Category category, ItemStack icon, String name, int count, @Nullable PartTier tier, boolean defective) {
    }

    public static ServerParts of(ItemStack server) {
        ItemContainerContents contents = server.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<Row> rows = new ArrayList<>();
        int tierSum = 0, tierWeight = 0;
        for (Category category : Category.values()) {
            Map<String, Row> byModel = new LinkedHashMap<>();
            for (int i = 0; i < category.slots; i++) {
                int slot = category.slot.applyAsInt(i);
                ItemStack part = slot < contents.getSlots() ? contents.getStackInSlot(slot) : ItemStack.EMPTY;
                if (part.isEmpty()) {
                    continue;
                }
                PartTier tier = PartTier.of(part);
                boolean defective = ComponentCondition.isDefective(part);
                String name = part.getHoverName().getString();
                String key = name + '|' + tier + '|' + defective;
                Row old = byModel.get(key);
                byModel.put(key, new Row(category, old == null ? part : old.icon, name,
                        (old == null ? 0 : old.count) + part.getCount(), tier, defective));
                if (tier != null) {
                    tierSum += tier.ordinal() * category.weight() * part.getCount();
                    tierWeight += category.weight() * part.getCount();
                }
            }
            rows.addAll(byModel.values());
        }
        PartTier overall = tierWeight == 0 ? null : PartTier.ofOrdinal(Math.round((float) tierSum / tierWeight));
        return new ServerParts(rows, overall);
    }
}
