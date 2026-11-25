package de.f4ked.data_center_addon.industry;

import com.example.datacenter.api.DataCenterApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The Purchase Terminal: buys raw materials ({@link Commodity}) for a factory and keeps them in its
 * inventory, from where hoppers, pipes or the player take them.
 * <ul>
 *   <li><b>Budget</b>: money paid in from the owner's wallet; purchases are paid from it.</li>
 *   <li><b>Wallet fallback</b>: when on, a purchase the budget cannot cover is paid from the wallet.</li>
 *   <li><b>Auto-reorder</b>: per commodity a target stock; every few seconds the terminal buys what is
 *       missing up to it (as far as the money and its space allow).</li>
 * </ul>
 * The terminal belongs to the account of the player who placed it; only that account can use it.
 */
public class PurchaseTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 18;
    /** Largest auto-reorder target per commodity, and its step in the menu. */
    public static final int MAX_TARGET = 576, TARGET_STEP = 16;
    private static final int REORDER_TICKS = 40;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What automation sees: it may take everything, but put nothing in. */
    private final IItemHandler output = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    @Nullable
    private UUID account;
    private long budgetCents;
    private boolean walletFallback;
    private final int[] targets = new int[Commodity.values().length];
    /** Money spent through this terminal, in cents (for the statistics). */
    private long spentCents;

    /** Synced to the open menu: budget (2 ints), fallback, then per commodity target and price. */
    public static final int DATA_BUDGET_LOW = 0, DATA_BUDGET_HIGH = 1, DATA_FALLBACK = 2, DATA_TARGETS = 3;
    public static final int DATA_PRICES = DATA_TARGETS + Commodity.values().length;
    public static final int DATA_COUNT = DATA_PRICES + Commodity.values().length;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_BUDGET_LOW) {
                return (int) budgetCents;
            }
            if (index == DATA_BUDGET_HIGH) {
                return (int) (budgetCents >>> 32);
            }
            if (index == DATA_FALLBACK) {
                return walletFallback ? 1 : 0;
            }
            if (index < DATA_PRICES) {
                return targets[index - DATA_TARGETS];
            }
            if (index < DATA_COUNT && level != null) {
                return (int) Math.min(Integer.MAX_VALUE, Commodity.values()[index - DATA_PRICES].priceCents(DataCenterApi.currentDate(level)));
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public PurchaseTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryContent.PURCHASE_TERMINAL_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public IItemHandler output() {
        return output;
    }

    @Nullable
    public UUID account() {
        return account;
    }

    public long budgetCents() {
        return budgetCents;
    }

    public void setAccount(@Nullable UUID account) {
        this.account = account;
        setChanged();
    }

    /** Whether the player belongs to the terminal's account (any player while it has none). */
    public boolean mayUse(ServerLevel level, Player player) {
        return account == null || account.equals(DataCenterApi.accountOf(level, player.getUUID()));
    }

    // ------------------------------------------------------------------ actions

    /** The menu buttons; see {@link PurchaseTerminalMenu#clickMenuButton}. */
    public enum Action { BUY_ONE, BUY_STACK, TARGET_DOWN, TARGET_UP, DEPOSIT_SMALL, DEPOSIT_LARGE, WITHDRAW_ALL, TOGGLE_FALLBACK }

    public static final long DEPOSIT_SMALL_CENTS = 100_00L, DEPOSIT_LARGE_CENTS = 1_000_00L;

    void apply(ServerLevel level, Player player, Action action, int commodity) {
        if (!mayUse(level, player)) {
            return;
        }
        UUID id = account != null ? account : DataCenterApi.accountOf(level, player.getUUID());
        Commodity c = commodity >= 0 && commodity < Commodity.values().length ? Commodity.values()[commodity] : null;
        switch (action) {
            case BUY_ONE -> {
                if (c != null) {
                    buy(level, id, c, 1);
                }
            }
            case BUY_STACK -> {
                if (c != null) {
                    buy(level, id, c, c.item().getDefaultMaxStackSize());
                }
            }
            case TARGET_DOWN -> {
                if (c != null) {
                    targets[c.ordinal()] = Math.max(0, targets[c.ordinal()] - TARGET_STEP);
                }
            }
            case TARGET_UP -> {
                if (c != null) {
                    targets[c.ordinal()] = Math.min(MAX_TARGET, targets[c.ordinal()] + TARGET_STEP);
                }
            }
            case DEPOSIT_SMALL -> deposit(level, id, DEPOSIT_SMALL_CENTS);
            case DEPOSIT_LARGE -> deposit(level, id, DEPOSIT_LARGE_CENTS);
            case WITHDRAW_ALL -> {
                if (budgetCents > 0) {
                    DataCenterApi.deposit(level, id, budgetCents);
                    budgetCents = 0;
                }
            }
            case TOGGLE_FALLBACK -> walletFallback = !walletFallback;
        }
        setChanged();
    }

    private void deposit(ServerLevel level, UUID id, long cents) {
        long take = Math.min(cents, Math.max(0, DataCenterApi.balanceCents(level, id)));
        if (take > 0 && DataCenterApi.withdraw(level, id, take)) {
            budgetCents += take;
        }
    }

    /**
     * Buys up to {@code count} items of the commodity: as many as fit into the inventory and as the
     * budget (and with the fallback the wallet) can pay. Returns how many were bought.
     */
    int buy(ServerLevel level, UUID id, Commodity c, int count) {
        LocalDate date = DataCenterApi.currentDate(level);
        long price = c.priceCents(date);
        int fits = space(c);
        int want = Math.min(count, fits);
        if (want <= 0) {
            return 0;
        }
        long wallet = walletFallback ? Math.max(0, DataCenterApi.balanceCents(level, id)) : 0;
        int affordable = (int) Math.min(want, (budgetCents + wallet) / price);
        if (affordable <= 0) {
            return 0;
        }
        long cost = affordable * price;
        long fromBudget = Math.min(budgetCents, cost);
        long fromWallet = cost - fromBudget;
        if (fromWallet > 0 && !DataCenterApi.withdraw(level, id, fromWallet)) {
            return 0;
        }
        budgetCents -= fromBudget;
        spentCents += cost;
        IndustryStats.get(level.getServer()).bought(id, c, affordable, cost, date.toEpochDay());
        ItemHandlerHelper.insertItemStacked(inventory, new ItemStack(c.item(), affordable), false);
        setChanged();
        return affordable;
    }

    /** How many more items of the commodity fit into the inventory. */
    private int space(Commodity c) {
        ItemStack probe = new ItemStack(c.item());
        int max = probe.getMaxStackSize();
        int space = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (s.isEmpty()) {
                space += max;
            } else if (ItemStack.isSameItemSameComponents(s, probe)) {
                space += max - s.getCount();
            }
        }
        return space;
    }

    private int stock(Commodity c) {
        int n = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (s.is(c.item())) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, PurchaseTerminalBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % REORDER_TICKS != 0 || be.account == null) {
            return;
        }
        for (Commodity c : Commodity.values()) {
            int missing = be.targets[c.ordinal()] - be.stock(c);
            if (missing > 0) {
                be.buy(serverLevel, be.account, c, missing);
            }
        }
    }

    /** Pays the budget back to the owner's wallet (the block is broken). */
    void refund(ServerLevel level) {
        if (budgetCents > 0 && account != null) {
            DataCenterApi.deposit(level, account, budgetCents);
            budgetCents = 0;
        }
    }

    // ------------------------------------------------------------------ menu and saving

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.data_center_addon.purchase_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PurchaseTerminalMenu(id, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        if (account != null) {
            tag.putUUID("account", account);
        }
        tag.putLong("budget", budgetCents);
        tag.putBoolean("walletFallback", walletFallback);
        tag.putIntArray("targets", targets);
        tag.putLong("spent", spentCents);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        account = tag.hasUUID("account") ? tag.getUUID("account") : null;
        budgetCents = Math.max(0, tag.getLong("budget"));
        walletFallback = tag.getBoolean("walletFallback");
        int[] saved = tag.getIntArray("targets");
        for (int i = 0; i < targets.length && i < saved.length; i++) {
            targets[i] = Math.max(0, Math.min(MAX_TARGET, saved[i]));
        }
        spentCents = tag.getLong("spent");
    }
}
