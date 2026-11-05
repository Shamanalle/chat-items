package com.kasper.chatitems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An immutable snapshot of a player's inventory ({@link SnapshotType#INV}) or ender chest
 * ({@link SnapshotType#ENDER}).
 * <p>
 * For {@link SnapshotType#INV}, the 6-row (54-slot) read-only chest layout is:
 * <ul>
 *   <li>Chest slots {@code 0..26} (rows 1–3): main storage inventory (player slots {@code 9..35})</li>
 *   <li>Chest slots {@code 27..35} (row 4): hotbar (player slots {@code 0..8})</li>
 *   <li>Chest slots {@code 36..44} (row 5): separator row</li>
 *   <li>Chest slots {@code 45..48} (row 6): armor ({@code 45}=helmet, {@code 46}=chestplate, {@code 47}=leggings, {@code 48}=boots)</li>
 *   <li>Chest slot {@code 49} (row 6): separator slot</li>
 *   <li>Chest slot {@code 50} (row 6): offhand item</li>
 *   <li>Chest slots {@code 51..53} (row 6): separator slots</li>
 * </ul>
 *
 * @param <S> the platform's cloned, immutable item stack representation
 */
public final class InventorySnapshot<S> {

    public static final int INV_MAIN_SIZE = 36;
    public static final int INV_ARMOR_SIZE = 4;
    public static final int ENDER_SIZE = 27;

    public static final int CHEST_SLOT_HELMET = 45;
    public static final int CHEST_SLOT_CHESTPLATE = 46;
    public static final int CHEST_SLOT_LEGGINGS = 47;
    public static final int CHEST_SLOT_BOOTS = 48;
    public static final int CHEST_SLOT_OFFHAND = 50;

    private final String id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final SnapshotType type;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<S> mainSlots;
    private final List<S> armorSlots;
    private final S offhandSlot;

    public InventorySnapshot(
            String id,
            UUID ownerUuid,
            String ownerName,
            SnapshotType type,
            long createdAtMillis,
            long expiresAtMillis,
            List<S> mainSlots,
            List<S> armorSlots,
            S offhandSlot
    ) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.type = type;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.mainSlots = Jv.copyOf(mainSlots);
        this.armorSlots = armorSlots == null ? Collections.emptyList() : Jv.copyOf(armorSlots);
        this.offhandSlot = offhandSlot;
    }

    public String id() {
        return id;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public SnapshotType type() {
        return type;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public List<S> mainSlots() {
        return mainSlots;
    }

    public List<S> armorSlots() {
        return armorSlots;
    }

    public S offhandSlot() {
        return offhandSlot;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    /**
     * Whether {@code chestSlot} in a 54-slot {@link SnapshotType#INV} container is a visual divider slot.
     */
    public static boolean isSeparatorSlot(int chestSlot) {
        return (chestSlot >= 36 && chestSlot <= 44)
                || chestSlot == 49
                || (chestSlot >= 51 && chestSlot <= 53);
    }

    /**
     * Builds the flat list of chest slots (54 for {@link SnapshotType#INV}, 27 for {@link SnapshotType#ENDER})
     * ready to populate a read-only chest menu.
     */
    public List<S> toChestSlots(S emptyStack, S separatorStack) {
        int size = type.chestSize();
        List<S> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(emptyStack);
        }
        if (type == SnapshotType.ENDER) {
            int limit = Math.min(ENDER_SIZE, mainSlots.size());
            for (int i = 0; i < limit; i++) {
                S item = mainSlots.get(i);
                out.set(i, item != null ? item : emptyStack);
            }
            return Collections.unmodifiableList(out);
        }
        // Main storage (player slots 9..35 -> chest slots 0..26)
        for (int playerSlot = 9; playerSlot < INV_MAIN_SIZE && playerSlot < mainSlots.size(); playerSlot++) {
            S item = mainSlots.get(playerSlot);
            out.set(playerSlot - 9, item != null ? item : emptyStack);
        }
        // Hotbar (player slots 0..8 -> chest slots 27..35)
        for (int hotbar = 0; hotbar < 9 && hotbar < mainSlots.size(); hotbar++) {
            S item = mainSlots.get(hotbar);
            out.set(27 + hotbar, item != null ? item : emptyStack);
        }
        // Separator slots
        for (int slot = 36; slot < 54; slot++) {
            if (isSeparatorSlot(slot)) {
                out.set(slot, separatorStack != null ? separatorStack : emptyStack);
            }
        }
        // Armor slots: 0=helmet(45), 1=chestplate(46), 2=leggings(47), 3=boots(48)
        for (int a = 0; a < INV_ARMOR_SIZE && a < armorSlots.size(); a++) {
            S item = armorSlots.get(a);
            out.set(CHEST_SLOT_HELMET + a, item != null ? item : emptyStack);
        }
        // Offhand slot (50)
        out.set(CHEST_SLOT_OFFHAND, offhandSlot != null ? offhandSlot : emptyStack);
        return Collections.unmodifiableList(out);
    }
}
