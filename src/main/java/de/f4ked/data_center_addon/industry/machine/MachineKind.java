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
            of(() -> IndustryContent.SILICON.get(), 1, 120, 40, in(() -> Items.QUARTZ, 1), in(() -> Items.COAL, 1)))),

    /** Ingots rolled thin: copper foil for circuit boards and capacitors, gold contacts for connectors and chips. */
    ROLLING_MILL(8_000_00L, () -> List.of(
            of(() -> IndustryContent.COPPER_FOIL.get(), 2, 80, 25, in(() -> Items.COPPER_INGOT, 1)),
            of(() -> IndustryContent.GOLD_CONTACTS.get(), 4, 100, 30, in(() -> Items.GOLD_INGOT, 1)))),

    /** Copper drawn into wire, the core of every cable. */
    WIRE_DRAWER(7_000_00L, () -> List.of(
            of(() -> IndustryContent.COPPER_WIRE.get(), 4, 60, 20, in(() -> Items.COPPER_INGOT, 1)))),

    /** Pressing and cutting: plastic parts from pellets, circuit board blanks from foil and glass, wafers from silicon. */
    PRESS(9_000_00L, () -> List.of(
            of(() -> IndustryContent.PLASTIC_PARTS.get(), 2, 60, 25, in(() -> IndustryContent.PLASTIC_PELLETS.get(), 4)),
            of(() -> IndustryContent.PCB_BLANK.get(), 1, 100, 35, in(() -> IndustryContent.COPPER_FOIL.get(), 1),
                    in(() -> Items.GLASS, 1)),
            of(() -> IndustryContent.WAFER.get(), 2, 120, 45, in(() -> IndustryContent.SILICON.get(), 1))));

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
