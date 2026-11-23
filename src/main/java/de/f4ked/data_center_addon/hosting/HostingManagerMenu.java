package de.f4ked.data_center_addon.hosting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/** The open Hosting Manager; no slots, the figures arrive as {@link HostingSnapshot}s. */
public class HostingManagerMenu extends AbstractContainerMenu {
    private final BlockPos managerPos;
    private final ContainerLevelAccess access;

    public HostingManagerMenu(int id, Inventory inventory, BlockPos managerPos) {
        super(HostingContent.MENU.get(), id);
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
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof HostingManagerBlock
                && player.canInteractWithBlock(pos, 4.0), true);
    }
}
