package de.f4ked.data_center_addon.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

/**
 * The open Purchase Terminal: its inventory (take only) above the player's, the budget, targets and
 * prices as {@link ContainerData}. The buttons are menu buttons ({@link #clickMenuButton}):
 * {@code id = action × 16 + commodity}.
 */
public class PurchaseTerminalMenu extends AbstractContainerMenu {
    /** Layout shared with the screen. */
    public static final int SLOTS_X = 306, SLOTS_Y = 46, INV_X = 306, INV_Y = 110;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final PurchaseTerminalBlockEntity terminal;

    /** Client side. */
    public PurchaseTerminalMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new ItemStackHandler(PurchaseTerminalBlockEntity.SLOTS),
                new SimpleContainerData(PurchaseTerminalBlockEntity.DATA_COUNT), null);
    }

    /** Server side. */
    PurchaseTerminalMenu(int id, Inventory inventory, PurchaseTerminalBlockEntity terminal, ContainerData data) {
        this(id, inventory, terminal.getBlockPos(), terminal.inventory(), data, terminal);
    }

    private PurchaseTerminalMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data,
                                 PurchaseTerminalBlockEntity terminal) {
        super(IndustryContent.PURCHASE_TERMINAL_MENU.get(), id);
        this.pos = pos.immutable();
        this.access = ContainerLevelAccess.create(inventory.player.level(), this.pos);
        this.data = data;
        this.terminal = terminal;
        for (int i = 0; i < PurchaseTerminalBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(items, i, SLOTS_X + (i % 9) * 18, SLOTS_Y + (i / 9) * 18) {
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

    public BlockPos pos() {
        return pos;
    }

    public long budgetCents() {
        return (data.get(PurchaseTerminalBlockEntity.DATA_BUDGET_LOW) & 0xFFFFFFFFL)
                | ((long) data.get(PurchaseTerminalBlockEntity.DATA_BUDGET_HIGH) << 32);
    }

    public boolean walletFallback() {
        return data.get(PurchaseTerminalBlockEntity.DATA_FALLBACK) != 0;
    }

    public int target(Commodity c) {
        return data.get(PurchaseTerminalBlockEntity.DATA_TARGETS + c.ordinal());
    }

    public long priceCents(Commodity c) {
        return data.get(PurchaseTerminalBlockEntity.DATA_PRICES + c.ordinal());
    }

    /** The id of a button for {@link #clickMenuButton}. */
    public static int button(PurchaseTerminalBlockEntity.Action action, int commodity) {
        return action.ordinal() * 16 + Math.max(0, commodity);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        PurchaseTerminalBlockEntity.Action[] actions = PurchaseTerminalBlockEntity.Action.values();
        int action = id / 16;
        if (terminal == null || action < 0 || action >= actions.length || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        terminal.apply(level, player, actions[action], id % 16);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= PurchaseTerminalBlockEntity.SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, PurchaseTerminalBlockEntity.SLOTS, slots.size(), true)) {
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
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof PurchaseTerminalBlock
                && player.canInteractWithBlock(p, 4.0), true);
    }
}
