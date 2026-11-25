package de.f4ked.data_center_addon.industry.hardware;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlock;
import de.f4ked.data_center_addon.industry.manager.FactoryMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Hardware Fab: builds the server part chosen in its screen (any CPU, GPU, RAM, storage, power
 * supply, mainboard or fan on sale today) from factory components ({@link HardwarePlan}).
 * <p>
 * Nine input slots take the components (by hand or automation), three output slots hold the finished
 * parts. A finished part carries the value of its components as the price paid for it, so Data Center's
 * Shop buys it back for at most 80 % of that.
 */
public class HardwareFabBlockEntity extends BlockEntity implements MenuProvider, FactoryMachine {
    public static final int INPUTS = 9, OUTPUTS = 3, SLOTS = INPUTS + OUTPUTS;
    public static final int ENERGY_CAPACITY = 100_000, MAX_RECEIVE = 4_000;
    private static final ResourceLocation PAID_CENTS = ResourceLocation.fromNamespaceAndPath("datacenter", "paid_cents");

    /** Why the fab is not working; the name gives {@code data_center_addon.industry.hardware.status.<name>}. */
    public enum Status { WORKING, NONE_SELECTED, NOT_ON_SALE, MISSING, NO_POWER, OUTPUT_FULL }

    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_ENERGY = 2, DATA_CAPACITY = 3, DATA_STATUS = 4,
            DATA_MATERIAL_LOW = 5, DATA_MATERIAL_HIGH = 6, DATA_COUNTS = 7;
    public static final int DATA_COUNT = DATA_COUNTS + HardwarePlan.INPUTS.length;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= INPUTS || accepts(stack);
        }
    };
    private final FabEnergy energy = new FabEnergy();

    @Nullable
    private ResourceLocation selected;
    private double progress;
    @Nullable
    private HardwarePlan.Plan plan;
    private Status status = Status.NONE_SELECTED;

    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < INPUTS && accepts(stack) ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < INPUTS ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < INPUTS && accepts(stack);
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            long material = plan == null ? 0 : plan.materialCents();
            if (index >= DATA_COUNTS && index < DATA_COUNT) {
                return plan == null ? 0 : plan.counts()[index - DATA_COUNTS];
            }
            return switch (index) {
                case DATA_PROGRESS -> (int) progress;
                case DATA_TOTAL -> plan == null ? 0 : plan.ticks();
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_MATERIAL_LOW -> (int) material;
                case DATA_MATERIAL_HIGH -> (int) (material >>> 32);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public HardwareFabBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryContent.HARDWARE_FAB_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public IItemHandler automation() {
        return automation;
    }

    public IEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public ResourceLocation selected() {
        return selected;
    }

    @Override
    public FactoryMachine.State factoryState() {
        return switch (status) {
            case WORKING -> FactoryMachine.State.WORKING;
            case NONE_SELECTED, MISSING -> FactoryMachine.State.IDLE;
            case NO_POWER -> FactoryMachine.State.NO_POWER;
            case NOT_ON_SALE, OUTPUT_FULL -> FactoryMachine.State.BLOCKED;
        };
    }

    @Override
    public ItemStack factoryOutput() {
        ShopEntry entry = selected == null ? null : HardwarePlan.entry(selected);
        return entry == null ? ItemStack.EMPTY : entry.createStack();
    }

    @Override
    public float factoryEnergy() {
        return (float) energy.getEnergyStored() / energy.getMaxEnergyStored();
    }

    static boolean accepts(ItemStack stack) {
        for (var p : HardwarePlan.INPUTS) {
            if (stack.is(p.item())) {
                return true;
            }
        }
        return false;
    }

    /** Chooses the part to build (from the screen); a change starts the run anew. */
    public void select(@Nullable ResourceLocation partId) {
        if (partId != null && (level == null || HardwarePlan.entry(partId) == null)) {
            return;
        }
        if (partId == null ? selected != null : !partId.equals(selected)) {
            selected = partId;
            progress = 0;
            plan = null;
            setChanged();
        }
    }

    // ------------------------------------------------------------------ work

    public static void serverTick(Level level, BlockPos pos, BlockState state, HardwareFabBlockEntity be) {
        boolean working = be.work(level);
        if (state.getValue(FactoryMachineBlock.ACTIVE) != working) {
            level.setBlock(pos, state.setValue(FactoryMachineBlock.ACTIVE, working), 3);
        }
    }

    private boolean work(Level level) {
        ShopEntry entry = selected == null ? null : HardwarePlan.entry(selected);
        if (entry == null) {
            status = Status.NONE_SELECTED;
            plan = null;
            return false;
        }
        if (plan == null || level.getGameTime() % 20 == 0) {
            plan = HardwarePlan.plan(entry, level, DataCenterApi.currentDate(level));
        }
        if (!HardwarePlan.buildable(entry, level)) {
            status = Status.NOT_ON_SALE;
            return false;
        }
        if (!hasInputs(plan)) {
            status = Status.MISSING;
            return false;
        }
        ItemStack result = entry.createStack();
        if (!outputFits(result)) {
            status = Status.OUTPUT_FULL;
            return false;
        }
        double speed = Config.machineSpeed();
        int draw = (int) Math.ceil(HardwarePlan.FE_PER_TICK * speed * Config.machineEnergy());
        if (energy.getEnergyStored() < draw) {
            status = Status.NO_POWER;
            return false;
        }
        status = Status.WORKING;
        energy.consume(draw);
        progress += speed;
        if (progress >= plan.ticks()) {
            finish(plan, result);
            progress = 0;
        }
        setChanged();
        return true;
    }

    private boolean hasInputs(HardwarePlan.Plan plan) {
        for (int i = 0; i < HardwarePlan.INPUTS.length; i++) {
            if (count(HardwarePlan.INPUTS[i].item()) < plan.counts()[i]) {
                return false;
            }
        }
        return true;
    }

    private int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int s = 0; s < INPUTS; s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private boolean outputFits(ItemStack result) {
        for (int s = INPUTS; s < SLOTS; s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void finish(HardwarePlan.Plan plan, ItemStack result) {
        for (int i = 0; i < HardwarePlan.INPUTS.length; i++) {
            int need = plan.counts()[i];
            for (int s = 0; s < INPUTS && need > 0; s++) {
                ItemStack stack = inventory.getStackInSlot(s);
                if (stack.is(HardwarePlan.INPUTS[i].item())) {
                    int take = Math.min(need, stack.getCount());
                    inventory.extractItem(s, take, false);
                    need -= take;
                }
            }
        }
        DataComponentType<?> paid = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PAID_CENTS);
        if (paid != null) {
            result.set((DataComponentType<Integer>) paid, (int) Math.min(Integer.MAX_VALUE, Math.max(1, plan.materialCents())));
        }
        for (int s = INPUTS; s < SLOTS; s++) {
            if (inventory.getStackInSlot(s).isEmpty()) {
                inventory.setStackInSlot(s, result);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ menu and saving

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.data_center_addon.hardware_fab");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new HardwareFabMenu(id, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putDouble("progress", progress);
        if (selected != null) {
            tag.putString("selected", selected.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
        }
        progress = Math.max(0, tag.getDouble("progress"));
        selected = tag.contains("selected") ? ResourceLocation.tryParse(tag.getString("selected")) : null;
    }

    private static final class FabEnergy extends EnergyStorage {
        FabEnergy() {
            super(ENERGY_CAPACITY, MAX_RECEIVE, 0);
        }

        void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }
    }
}
