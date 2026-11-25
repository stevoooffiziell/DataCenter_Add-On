package de.f4ked.data_center_addon.industry.chipfab;

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

/** The open chip production line: three inputs, two outputs (take only), the player's inventory and the line's figures. */
public class ChipFabMenu extends AbstractContainerMenu {
    /** Layout shared with the screen. */
    public static final int IN_X = 30, IO_Y = 30, OUT_X = 124, INV_X = 8, INV_Y = 96;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client side. */
    public ChipFabMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new ItemStackHandler(ChipFabBlockEntity.SLOTS), new SimpleContainerData(ChipFabBlockEntity.DATA_COUNT));
    }

    /** Server side. */
    ChipFabMenu(int id, Inventory inventory, ChipFabBlockEntity fab, ContainerData data) {
        this(id, inventory, fab.getBlockPos(), fab.inventory(), data);
    }

    private ChipFabMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(IndustryContent.CHIP_FAB_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos.immutable());
        this.data = data;
        for (int i = 0; i < ChipFabBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, i, IN_X + i * 18, IO_Y));
        }
        for (int i = 0; i < ChipFabBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, ChipFabBlockEntity.INPUTS + i, OUT_X + i * 18, IO_Y) {
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ChipFabBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, ChipFabBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, ChipFabBlockEntity.INPUTS, false)) {
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
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof ChipFabControllerBlock
                && player.canInteractWithBlock(p, 4.0), true);
    }
}
