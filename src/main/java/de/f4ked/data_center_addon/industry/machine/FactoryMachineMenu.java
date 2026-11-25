package de.f4ked.data_center_addon.industry.machine;

import de.f4ked.data_center_addon.industry.IndustryContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The open factory machine: two inputs, two outputs (take only), the player's inventory, and its figures. */
public class FactoryMachineMenu extends AbstractContainerMenu {
    /** Layout shared with the screen. */
    public static final int IN_X = 44, IO_Y = 35, OUT_X = 116, INV_X = 8, INV_Y = 84;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client side. */
    public FactoryMachineMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new ItemStackHandler(FactoryMachineBlockEntity.SLOTS),
                new SimpleContainerData(FactoryMachineBlockEntity.DATA_COUNT));
    }

    /** Server side. */
    FactoryMachineMenu(int id, Inventory inventory, FactoryMachineBlockEntity machine, ContainerData data) {
        this(id, inventory, machine.getBlockPos(), machine.inventory(), data);
    }

    private FactoryMachineMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(IndustryContent.MACHINE_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos.immutable());
        this.data = data;
        for (int i = 0; i < FactoryMachineBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, i, IN_X + i * 18, IO_Y));
        }
        for (int i = 0; i < FactoryMachineBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, FactoryMachineBlockEntity.INPUTS + i, OUT_X + i * 18, IO_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    public MachineKind kind() {
        return MachineKind.byOrdinal(data.get(FactoryMachineBlockEntity.DATA_KIND));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = FactoryMachineBlockEntity.SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, FactoryMachineBlockEntity.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof FactoryMachineBlock
                && player.canInteractWithBlock(p, 4.0), true);
    }
}
