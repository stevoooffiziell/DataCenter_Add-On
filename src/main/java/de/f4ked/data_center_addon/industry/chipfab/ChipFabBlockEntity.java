package de.f4ked.data_center_addon.industry.chipfab;

import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlock;
import de.f4ked.data_center_addon.industry.machine.MachineRecipe;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

import static de.f4ked.data_center_addon.industry.machine.MachineRecipe.in;
import static de.f4ked.data_center_addon.industry.machine.MachineRecipe.of;

/**
 * The controller of the chip production line ({@link ChipFabStructure}): three input slots, two output
 * slots and a large power buffer. It works only while the line is formed; every working lithography unit
 * makes it one step faster (and draws that much more power per tick).
 * <p>
 * Automation as with the factory machines: items in from any side go to the inputs (one kind per slot),
 * items out come from the outputs; power from every side.
 */
public class ChipFabBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUTS = 3, OUTPUTS = 2, SLOTS = INPUTS + OUTPUTS;
    public static final int ENERGY_CAPACITY = 200_000, MAX_RECEIVE = 8_000;
    private static final int SCAN_TICKS = 40;

    /** The line's recipes: wafer, gold contacts and redstone to microchips. */
    public static final List<MachineRecipe> RECIPES = List.of(
            of(() -> IndustryContent.MICROCHIP.get(), 4, 400, 80, in(() -> IndustryContent.WAFER.get(), 1),
                    in(() -> IndustryContent.GOLD_CONTACTS.get(), 2), in(() -> Items.REDSTONE, 2)));

    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_ENERGY = 2, DATA_CAPACITY = 3, DATA_WALLS = 4,
            DATA_UNITS = 5, DATA_STATUS = 6, DATA_SPEED = 7, DATA_FE_PER_TICK = 8, DATA_COUNT = 9;

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
    private double progress;
    private int recipe = -1;
    private int lastDraw;
    private ChipFabStructure.Scan scan = new ChipFabStructure.Scan(0, 0, ChipFabStructure.Status.NO_WALLS);
    private boolean scanned;

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
            if (slot >= INPUTS || !accepts(stack)) {
                return stack;
            }
            if (inventory.getStackInSlot(slot).isEmpty()) {
                for (int other = 0; other < INPUTS; other++) {
                    if (other != slot && ItemStack.isSameItemSameComponents(inventory.getStackInSlot(other), stack)) {
                        return stack;
                    }
                }
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
            return slot < INPUTS && accepts(stack);
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
                case DATA_WALLS -> scan.walls();
                case DATA_UNITS -> scan.units();
                case DATA_STATUS -> scan.status().ordinal();
                case DATA_SPEED -> scan.speed();
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

    public ChipFabBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryContent.CHIP_FAB_ENTITY.get(), pos, state);
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

    static boolean accepts(ItemStack stack) {
        for (MachineRecipe r : RECIPES) {
            if (r.uses(stack)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ work

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChipFabBlockEntity be) {
        if (level.getGameTime() % SCAN_TICKS == 0 || !be.scanned) {
            be.scan = ChipFabStructure.scan(level, pos);
            be.scanned = true;
        }
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
        int units = scan.speed();
        if (r == null || units <= 0 || !outputFits(r.result())) {
            return;
        }
        double speed = units * Config.machineSpeed();
        int draw = (int) Math.ceil(r.fePerTick() * speed * Config.machineEnergy());
        if (energy.getEnergyStored() < draw) {
            return;
        }
        energy.consume(draw);
        lastDraw = draw;
        progress += speed;
        if (progress >= r.ticks()) {
            finish(r);
            progress = 0;
        }
        setChanged();
    }

    private MachineRecipe current() {
        return recipe >= 0 && recipe < RECIPES.size() ? RECIPES.get(recipe) : null;
    }

    private int findRecipe() {
        for (int i = 0; i < RECIPES.size(); i++) {
            if (hasInputs(RECIPES.get(i))) {
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
        return Component.translatable("block.data_center_addon.chip_fab_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChipFabMenu(id, playerInventory, this, data);
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

    private static final class FabEnergy extends EnergyStorage {
        FabEnergy() {
            super(ENERGY_CAPACITY, MAX_RECEIVE, 0);
        }

        void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }
    }
}
