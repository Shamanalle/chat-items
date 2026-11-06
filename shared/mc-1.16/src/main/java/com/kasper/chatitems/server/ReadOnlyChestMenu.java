package com.kasper.chatitems.server;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A server-side {@link ChestMenu} for Minecraft 1.16.5 (where {@link #clicked} returns an
 * {@link ItemStack} rather than {@code void}) that denies all item interaction and resyncs slots.
 */
public final class ReadOnlyChestMenu extends ChestMenu {

    private final String snapshotId;
    private final String parentSnapshotId;

    public ReadOnlyChestMenu(int containerId, Inventory playerInventory, Container snapshotContainer, int rows) {
        this(containerId, playerInventory, snapshotContainer, rows, null, null);
    }

    public ReadOnlyChestMenu(
            int containerId,
            Inventory playerInventory,
            Container snapshotContainer,
            int rows,
            String snapshotId,
            String parentSnapshotId
    ) {
        super(rows == 6 ? MenuType.GENERIC_9x6 : rows == 4 ? MenuType.GENERIC_9x4 : MenuType.GENERIC_9x3,
                containerId, playerInventory, snapshotContainer, rows);
        this.snapshotId = snapshotId;
        this.parentSnapshotId = parentSnapshotId;
    }

    public String snapshotId() {
        return snapshotId;
    }

    public String parentSnapshotId() {
        return parentSnapshotId;
    }

    @Override
    public ItemStack clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        McCompat.resyncMenu(player, this);
        ChatItemsServer.onMenuClick(player, snapshotId, parentSnapshotId, slotId);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
