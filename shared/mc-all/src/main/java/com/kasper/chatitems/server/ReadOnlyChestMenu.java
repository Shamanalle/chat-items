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
 * A server-side {@link ChestMenu} (6 rows for {@code [inv]}, 3 rows for {@code [ender]}) that denies
 * all item movement, pickup, placement, hotbar swapping, dragging, shift-clicking, and dropping,
 * and resyncs slots to the client on any click attempt.
 */
public final class ReadOnlyChestMenu extends ChestMenu {

    public ReadOnlyChestMenu(int containerId, Inventory playerInventory, Container snapshotContainer, int rows) {
        super(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3,
                containerId, playerInventory, snapshotContainer, rows);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        McCompat.resyncMenu(player, this);
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
