package de.f4ked.data_center_addon.industry.manager;

import net.minecraft.world.item.ItemStack;

/** What the Factory Manager reads from a factory machine. */
public interface FactoryMachine {
    /** The states; the name gives {@code data_center_addon.industry.manager.state.<name>}. */
    enum State { WORKING, IDLE, NO_POWER, BLOCKED }

    State factoryState();

    /** What the machine makes right now (empty when nothing). */
    ItemStack factoryOutput();

    /** Power stored, 0..1. */
    float factoryEnergy();
}
