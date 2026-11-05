package com.kasper.chatitems;

/**
 * Kind of read-only container snapshot shared in chat.
 */
public enum SnapshotType {
    /** Full player inventory (36 main slots + 4 armor + 1 offhand) shown in a 6-row (54-slot) chest. */
    INV(6, 54),
    /** Player's ender chest (27 slots) shown in a 3-row (27-slot) chest. */
    ENDER(3, 27);

    private final int rows;
    private final int chestSize;

    SnapshotType(int rows, int chestSize) {
        this.rows = rows;
        this.chestSize = chestSize;
    }

    public int rows() {
        return rows;
    }

    public int chestSize() {
        return chestSize;
    }
}
