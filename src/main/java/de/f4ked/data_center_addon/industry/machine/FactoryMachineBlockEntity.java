package de.f4ked.data_center_addon.industry.machine;

import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.manager.FactoryMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * A factory machine ({@link MachineKind}): two input slots, two output slots, a power buffer (FE).
 * <p>
 * While its inputs make up a recipe, the output has room and there is power, it works: every tick it
 * draws the recipe's power and moves on by the machine speed; when the recipe's time is done the inputs
 * are used up and the result goes to the outputs.
 * <p>
 * Automation: items put in from any side go to the input slots (only items one of the machine's recipes
 * uses), items taken out come from the output slots. Power is accepted from every side.
 */
public class FactoryMachineBlockEntity extends BlockEntity implements MenuProvider, FactoryMachine {
    public static final int INPUTS = 2, OUTPUTS = 2, SLOTS = INPUTS + OUTPUTS;
    public static final int ENERGY_CAPACITY = 20_000, MAX_RECEIVE = 1_000;

    /** Synced to the open menu. */
    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_ENERGY = 2, DATA_CAPACITY = 3, DATA_KIND = 4,
            DATA_FE_PER_TICK = 5, DATA_COUNT = 6;

    private final MachineKind kind;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= INPUTS || kind.accepts(stack);
        }
    };

    /** A power buffer the machine draws from itself; outside it only takes power. */
    private final MachineEnergy energy = new MachineEnergy();

    /** Progress of the current run (at speed 1 one per tick) and the recipe it belongs to; -1 none. */
    private double progress;
    private int recipe = -1;
    /** Power drawn in the last tick (for the screen). */
    private int lastDraw;
    private FactoryMachine.State state = FactoryMachine.State.IDLE;

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
            if (slot >= INPUTS || !kind.accepts(stack)) {
                return stack;
            }
            // keep one kind per input slot, so two-input recipes get both inputs
            ItemStack other = inventory.getStackInSlot(1 - slot);
            if (inventory.getStackInSlot(slot).isEmpty() && ItemStack.isSameItemSameComponents(other, stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
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
            return slot < INPUTS && kind.accepts(stack);
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            MachineRecipe r = current();
            return switch (index) {
                case DATA_PROGRESS -> (int) progress;
                case DATA_TOTAL -> r == null ? 0 : r.ticks();
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_KIND -> kind.ordinal();
                case DATA_FE_PER_TICK -> lastDraw;
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

    public FactoryMachineBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryContent.MACHINE_ENTITY.get(), pos, state);
        this.kind = state.getBlock() instanceof FactoryMachineBlock block ? block.kind() : MachineKind.SMELTER;
    }

    public MachineKind kind() {
        return kind;
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

    // ------------------------------------------------------------------ work

    public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryMachineBlockEntity be) {
        be.work();
        boolean active = be.lastDraw > 0;
        if (state.getValue(FactoryMachineBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(FactoryMachineBlock.ACTIVE, active), 3);
        }
    }

    private void work() {
        lastDraw = 0;
        int found = findRecipe();
        if (found != recipe) {
            recipe = found;
            progress = 0;
            setChanged();
        }
        MachineRecipe r = current();
        if (r == null) {
            state = FactoryMachine.State.IDLE;
            return;
        }
        if (!outputFits(r.result())) {
            state = FactoryMachine.State.BLOCKED;
            return;
        }
        double speed = Config.machineSpeed();
        int draw = (int) Math.ceil(r.fePerTick() * speed * Config.machineEnergy());
        if (energy.getEnergyStored() < draw) {
            state = FactoryMachine.State.NO_POWER;
            return;
        }
        state = FactoryMachine.State.WORKING;
        energy.consume(draw);
        lastDraw = draw;
        progress += speed;
        if (progress >= r.ticks()) {
            finish(r);
            progress = 0;
        }
        setChanged();
    }

    @Override
    public FactoryMachine.State factoryState() {
        return state;
    }

    @Override
    public ItemStack factoryOutput() {
        MachineRecipe r = current();
        return r == null ? ItemStack.EMPTY : r.result();
    }

    @Override
    public float factoryEnergy() {
        return (float) energy.getEnergyStored() / energy.getMaxEnergyStored();
    }

    @Nullable
    private MachineRecipe current() {
        return recipe >= 0 && recipe < kind.recipes().size() ? kind.recipes().get(recipe) : null;
    }

    /** The first recipe the input slots can pay for; -1 if none. */
    private int findRecipe() {
        java.util.List<MachineRecipe> recipes = kind.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            if (hasInputs(recipes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasInputs(MachineRecipe r) {
        for (MachineRecipe.Input input : r.inputs()) {
            int have = 0;
            for (int s = 0; s < INPUTS; s++) {
                ItemStack stack = inventory.getStackInSlot(s);
                if (input.matches(stack)) {
                    have += stack.getCount();
                }
            }
            if (have < input.count()) {
                return false;
            }
        }
        return true;
    }

    private boolean outputFits(ItemStack result) {
        int left = result.getCount();
        for (int s = INPUTS; s < SLOTS && left > 0; s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack.isEmpty()) {
                left -= result.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, result)) {
                left -= stack.getMaxStackSize() - stack.getCount();
            }
        }
        return left <= 0;
    }

    private void finish(MachineRecipe r) {
        for (MachineRecipe.Input input : r.inputs()) {
            int need = input.count();
            for (int s = 0; s < INPUTS && need > 0; s++) {
                ItemStack stack = inventory.getStackInSlot(s);
                if (input.matches(stack)) {
                    int take = Math.min(need, stack.getCount());
                    inventory.extractItem(s, take, false);
                    need -= take;
                }
            }
        }
        ItemStack result = r.result();
        for (int s = INPUTS; s < SLOTS && !result.isEmpty(); s++) {
            result = inventory.insertItem(s, result, false);
        }
    }

    // ------------------------------------------------------------------ menu and saving

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.data_center_addon." + kind.id());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FactoryMachineMenu(id, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putDouble("progress", progress);
        tag.putInt("recipe", recipe);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
        }
        progress = Math.max(0, tag.getDouble("progress"));
        recipe = tag.contains("recipe") ? tag.getInt("recipe") : -1;
    }

    /** A buffer that takes power from outside and is drawn by the machine itself. */
    private static final class MachineEnergy extends EnergyStorage {
        MachineEnergy() {
            super(ENERGY_CAPACITY, MAX_RECEIVE, 0);
        }

        void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }
    }
}
