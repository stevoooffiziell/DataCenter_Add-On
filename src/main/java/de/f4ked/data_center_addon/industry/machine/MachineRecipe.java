package de.f4ked.data_center_addon.industry.machine;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * One process of a factory machine: inputs (in any order across the input slots), one output, how long it
 * takes and how much power it draws while it runs.
 *
 * @param ticks     time at speed 1, in ticks
 * @param fePerTick power drawn per tick while running
 */
public record MachineRecipe(List<Input> inputs, Supplier<Item> output, int count, int ticks, int fePerTick) {

    /** {@code count} items of one kind. */
    public record Input(Supplier<Item> item, int count) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.is(item.get());
        }
    }

    public static Input in(Supplier<Item> item, int count) {
        return new Input(item, count);
    }

    public static MachineRecipe of(Supplier<Item> output, int count, int ticks, int fePerTick, Input... inputs) {
        return new MachineRecipe(List.of(inputs), output, count, ticks, fePerTick);
    }

    public ItemStack result() {
        return new ItemStack(output.get(), count);
    }

    /** Whether the item is used by this recipe. */
    public boolean uses(ItemStack stack) {
        for (Input input : inputs) {
            if (input.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Total energy of one run at speed 1. */
    public long energy() {
        return (long) ticks * fePerTick;
    }
}
