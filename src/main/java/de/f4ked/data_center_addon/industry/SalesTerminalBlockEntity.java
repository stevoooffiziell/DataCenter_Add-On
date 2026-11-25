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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Sales Terminal: buys the factory's materials and components ({@link Product}). Whatever lands in
 * its inbox (by hand, hopper or pipe) is sold once a second at the current market price
 * ({@link SalesMarket}) and paid into the owner's wallet. Only products are taken; nothing comes out.
 */
public class SalesTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 9;
    private static final int SELL_TICKS = 20;

    private final ItemStackHandler inbox = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return Product.byItem(stack.getItem()) != null;
        }
    };

    /** What automation sees: products go in, nothing comes out. */
    private final IItemHandler input = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inbox.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inbox.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inbox.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inbox.isItemValid(slot, stack);
        }
    };

    @Nullable
    private UUID account;
    /** Paid so far, in cents, and the fraction of a cent not yet paid. */
    private long revenueCents;
    private double owedCents;

    /** Synced to the menu: revenue (2 ints), then per product the price now (cents) and the demand (‰). */
    public static final int DATA_REVENUE_LOW = 0, DATA_REVENUE_HIGH = 1, DATA_PRICES = 2;
    public static final int DATA_DEMAND = DATA_PRICES + Product.values().length;
    public static final int DATA_COUNT = DATA_DEMAND + Product.values().length;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_REVENUE_LOW) {
                return (int) revenueCents;
            }
            if (index == DATA_REVENUE_HIGH) {
                return (int) (revenueCents >>> 32);
            }
            if (!(level instanceof ServerLevel server) || account == null || index >= DATA_COUNT) {
                return 0;
            }
            long today = DataCenterApi.currentDate(server).toEpochDay();
            boolean price = index < DATA_DEMAND;
            Product p = Product.values()[index - (price ? DATA_PRICES : DATA_DEMAND)];
            double demand = SalesMarket.get(server.getServer()).demand(account, p, today);
            return price ? (int) Math.round(p.baseCents(DataCenterApi.currentDate(server)) * demand) : (int) Math.round(demand * 1000);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SalesTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryContent.SALES_TERMINAL_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inbox() {
        return inbox;
    }

    public IItemHandler input() {
        return input;
    }

    @Nullable
    public UUID account() {
        return account;
    }

    public void setAccount(@Nullable UUID account) {
        this.account = account;
        setChanged();
    }

    public boolean mayUse(ServerLevel level, Player player) {
        return account == null || account.equals(DataCenterApi.accountOf(level, player.getUUID()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SalesTerminalBlockEntity be) {
        if (level instanceof ServerLevel server && level.getGameTime() % SELL_TICKS == 0 && be.account != null) {
            be.sellAll(server);
        }
    }

    /** Sells everything in the inbox and pays the whole cents into the owner's wallet. */
    private void sellAll(ServerLevel server) {
        SalesMarket market = SalesMarket.get(server.getServer());
        var date = DataCenterApi.currentDate(server);
        long today = date.toEpochDay();
        boolean sold = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = inbox.getStackInSlot(i);
            Product p = stack.isEmpty() ? null : Product.byItem(stack.getItem());
            if (p == null) {
                continue;
            }
            double pay = market.sell(account, p, p.baseCents(date), stack.getCount(), today);
            owedCents += pay;
            IndustryStats.get(server.getServer()).sold(account, p, stack.getCount(), Math.round(pay), today);
            inbox.setStackInSlot(i, ItemStack.EMPTY);
            sold = true;
        }
        long whole = (long) Math.floor(owedCents);
        if (whole > 0) {
            DataCenterApi.deposit(server, account, whole);
            owedCents -= whole;
            revenueCents += whole;
        }
        if (sold) {
            setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.data_center_addon.sales_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SalesTerminalMenu(id, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inbox", inbox.serializeNBT(registries));
        if (account != null) {
            tag.putUUID("account", account);
        }
        tag.putLong("revenue", revenueCents);
        tag.putDouble("owed", owedCents);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inbox.deserializeNBT(registries, tag.getCompound("inbox"));
        account = tag.hasUUID("account") ? tag.getUUID("account") : null;
        revenueCents = tag.getLong("revenue");
        owedCents = Math.max(0, tag.getDouble("owed"));
    }
}
