package de.f4ked.data_center_addon.industry.hardware;

import de.f4ked.data_center_addon.industry.IndustryContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
import org.jetbrains.annotations.Nullable;

/**
 * The open Hardware Fab: nine component inputs, three outputs (take only), the player's inventory and the
 * fab's figures. The chosen part arrives when the menu opens and changes through {@code HardwareSelect}.
 */
public class HardwareFabMenu extends AbstractContainerMenu {
    /** Layout shared with the screen. */
    public static final int IN_X = 306, IN_Y = 82, OUT_X = 380, INV_X = 306, INV_Y = 154;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    @Nullable
    private ResourceLocation selected;

    /** Client side: the position and the chosen part come with the opening packet. */
    public HardwareFabMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), new ItemStackHandler(HardwareFabBlockEntity.SLOTS),
                new SimpleContainerData(HardwareFabBlockEntity.DATA_COUNT));
        this.selected = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    /** Server side. */
    HardwareFabMenu(int id, Inventory inventory, HardwareFabBlockEntity fab, ContainerData data) {
        this(id, inventory, fab.getBlockPos(), fab.inventory(), data);
        this.selected = fab.selected();
    }

    private HardwareFabMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(IndustryContent.HARDWARE_FAB_MENU.get(), id);
        this.pos = pos.immutable();
        this.access = ContainerLevelAccess.create(inventory.player.level(), this.pos);
        this.data = data;
        for (int i = 0; i < HardwareFabBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, i, IN_X + (i % 3) * 18, IN_Y + (i / 3) * 18));
        }
        for (int i = 0; i < HardwareFabBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, HardwareFabBlockEntity.INPUTS + i, OUT_X, IN_Y + i * 18) {
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

    @Nullable
    public ResourceLocation selected() {
        return selected;
    }

    /** The screen's choice, shown at once (the server confirms it with the next figures). */
    public void setSelected(@Nullable ResourceLocation selected) {
        this.selected = selected;
    }

    public int get(int index) {
        return data.get(index);
    }

    public long materialCents() {
        return (data.get(HardwareFabBlockEntity.DATA_MATERIAL_LOW) & 0xFFFFFFFFL)
                | ((long) data.get(HardwareFabBlockEntity.DATA_MATERIAL_HIGH) << 32);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < HardwareFabBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, HardwareFabBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, HardwareFabBlockEntity.INPUTS, false)) {
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
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof HardwareFabBlock
                && player.canInteractWithBlock(p, 4.0), true);
    }
}
