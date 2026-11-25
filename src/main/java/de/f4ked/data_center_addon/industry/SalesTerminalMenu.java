package de.f4ked.data_center_addon.industry;

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

/** The open Sales Terminal: its inbox (products only) beside the player's inventory, prices and demand as data. */
public class SalesTerminalMenu extends AbstractContainerMenu {
    /** Layout shared with the screen. */
    public static final int INBOX_X = 306, INBOX_Y = 46, INV_X = 306, INV_Y = 122;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client side. */
    public SalesTerminalMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new ItemStackHandler(SalesTerminalBlockEntity.SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return Product.byItem(stack.getItem()) != null;
            }
        }, new SimpleContainerData(SalesTerminalBlockEntity.DATA_COUNT));
    }

    /** Server side. */
    SalesTerminalMenu(int id, Inventory inventory, SalesTerminalBlockEntity terminal, ContainerData data) {
        this(id, inventory, terminal.getBlockPos(), terminal.inbox(), data);
    }

    private SalesTerminalMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(IndustryContent.SALES_TERMINAL_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos.immutable());
        this.data = data;
        for (int i = 0; i < SalesTerminalBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(items, i, INBOX_X + (i % 3) * 18, INBOX_Y + (i / 3) * 18));
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

    public long revenueCents() {
        return (data.get(SalesTerminalBlockEntity.DATA_REVENUE_LOW) & 0xFFFFFFFFL)
                | ((long) data.get(SalesTerminalBlockEntity.DATA_REVENUE_HIGH) << 32);
    }

    public long priceCents(Product p) {
        return data.get(SalesTerminalBlockEntity.DATA_PRICES + p.ordinal());
    }

    /** Demand 0..1. */
    public float demand(Product p) {
        return data.get(SalesTerminalBlockEntity.DATA_DEMAND + p.ordinal()) / 1000f;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SalesTerminalBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, SalesTerminalBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SalesTerminalBlockEntity.SLOTS, false)) {
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
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof SalesTerminalBlock
                && player.canInteractWithBlock(p, 4.0), true);
    }
}
