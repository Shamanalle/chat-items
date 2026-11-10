package com.kasper.chatitems.server;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A server-side {@link ChestMenu} (6 rows for {@code [inv]}, 3 rows for {@code [ender]}) that denies
 * all item movement, pickup, placement, hotbar swapping, dragging, shift-clicking, and dropping,
 * and resyncs slots to the client on any click attempt (Minecraft 26.x).
 */
public final class ReadOnlyChestMenu extends ChestMenu {

    private static final int[] EMPTY_PATH = new int[0];

    private final String snapshotId;
    private final int[] slotPath;

    public ReadOnlyChestMenu(int containerId, Inventory playerInventory, Container snapshotContainer, int rows) {
        this(containerId, playerInventory, snapshotContainer, rows, null, EMPTY_PATH);
    }

    public ReadOnlyChestMenu(
            int containerId,
            Inventory playerInventory,
            Container snapshotContainer,
            int rows,
            String snapshotId,
            int[] slotPath
    ) {
        super(rows == 6 ? MenuType.GENERIC_9x6 : rows == 4 ? MenuType.GENERIC_9x4 : MenuType.GENERIC_9x3,
                containerId, playerInventory, snapshotContainer, rows);
        this.snapshotId = snapshotId;
        this.slotPath = slotPath == null || slotPath.length == 0 ? EMPTY_PATH : slotPath.clone();
    }

    public String snapshotId() {
        return snapshotId;
    }

    public int[] slotPath() {
        return slotPath.clone();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        broadcastChanges();
        McCompat.resyncMenu(player, this);
        if ((input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) && (button == 0 || button == 1)) {
            ChatItemsServer.onMenuClick(player, snapshotId, slotPath, slotId);
        }
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
