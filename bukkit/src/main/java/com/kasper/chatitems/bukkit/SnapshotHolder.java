package com.kasper.chatitems.bukkit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker {@link InventoryHolder} for read-only {@code [inv]} and {@code [ender]} snapshot windows.
 * Any click, drag, or creative action in a view whose top inventory has this holder is cancelled.
 */
public final class SnapshotHolder implements InventoryHolder {

    private static final int[] EMPTY_PATH = new int[0];

    private final String snapshotId;
    private final int[] slotPath;

    public SnapshotHolder(String snapshotId) {
        this(snapshotId, EMPTY_PATH);
    }

    public SnapshotHolder(String snapshotId, int[] slotPath) {
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
    public Inventory getInventory() {
        return null;
    }
}
