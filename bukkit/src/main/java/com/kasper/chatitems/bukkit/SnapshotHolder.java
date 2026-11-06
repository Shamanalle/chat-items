package com.kasper.chatitems.bukkit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker {@link InventoryHolder} for read-only {@code [inv]} and {@code [ender]} snapshot windows.
 * Any click, drag, or creative action in a view whose top inventory has this holder is cancelled.
 */
public final class SnapshotHolder implements InventoryHolder {

    private final String snapshotId;
    private final String parentSnapshotId;

    public SnapshotHolder(String snapshotId) {
        this(snapshotId, null);
    }

    public SnapshotHolder(String snapshotId, String parentSnapshotId) {
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
    public Inventory getInventory() {
        return null;
    }
}
