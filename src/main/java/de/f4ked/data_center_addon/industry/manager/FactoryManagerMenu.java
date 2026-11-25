package de.f4ked.data_center_addon.industry.manager;

import de.f4ked.data_center_addon.industry.IndustryContent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/** The open Factory Manager; no slots, the figures arrive as {@link FactorySnapshot}s. */
public class FactoryManagerMenu extends AbstractContainerMenu {
    private final BlockPos managerPos;
    private final ContainerLevelAccess access;

    public FactoryManagerMenu(int id, Inventory inventory, BlockPos managerPos) {
        super(IndustryContent.FACTORY_MANAGER_MENU.get(), id);
        this.managerPos = managerPos.immutable();
        this.access = ContainerLevelAccess.create(inventory.player.level(), this.managerPos);
    }

    public BlockPos managerPos() {
        return managerPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof FactoryManagerBlock
                && player.canInteractWithBlock(pos, 4.0), true);
    }
}
