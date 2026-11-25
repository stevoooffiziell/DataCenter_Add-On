package de.f4ked.data_center_addon.industry.machine;

import de.f4ked.data_center_addon.industry.IndustryContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static de.f4ked.data_center_addon.industry.machine.MachineRecipe.in;
import static de.f4ked.data_center_addon.industry.machine.MachineRecipe.of;

/**
 * The factory machines. Each is a block with two input and two output slots, a power buffer and its
 * own recipes ({@link MachineRecipe}); everything else is shared ({@link FactoryMachineBlockEntity}).
 */
public enum MachineKind {
    /** Ores to ingots (faster than a furnace, on power), quartz and coal to silicon, sand to glass. */
    SMELTER(6_000_00L, () -> List.of(
            of(() -> Items.COPPER_INGOT, 1, 60, 20, in(() -> Items.RAW_COPPER, 1)),
            of(() -> Items.IRON_INGOT, 1, 60, 20, in(() -> Items.RAW_IRON, 1)),
            of(() -> Items.GOLD_INGOT, 1, 60, 20, in(() -> Items.RAW_GOLD, 1)),
            of(() -> Items.GLASS, 1, 40, 15, in(() -> Items.SAND, 1)),
            of(() -> IndustryContent.SILICON.get(), 1, 120, 40, in(() -> Items.QUARTZ, 1), in(() -> Items.COAL, 1))));

    /** Shop price in US cents. */
    public final long priceCents;
    private final Supplier<List<MachineRecipe>> recipes;
    private List<MachineRecipe> cached;

    MachineKind(long priceCents, Supplier<List<MachineRecipe>> recipes) {
        this.priceCents = priceCents;
        this.recipes = recipes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public List<MachineRecipe> recipes() {
        if (cached == null) {
            cached = recipes.get();
        }
        return cached;
    }

    /** Whether any recipe of the machine uses the item. */
    public boolean accepts(ItemStack stack) {
        for (MachineRecipe r : recipes()) {
            if (r.uses(stack)) {
                return true;
            }
        }
        return false;
    }

    public static MachineKind byOrdinal(int ordinal) {
        MachineKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SMELTER;
    }
}
