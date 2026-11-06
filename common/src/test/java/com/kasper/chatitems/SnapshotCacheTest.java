package com.kasper.chatitems;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotCacheTest {

    @Test
    void storesAndMapsInventoryAndEnderSnapshots() {
        SnapshotCache<String> cache = new SnapshotCache<>(16);
        UUID owner = UUID.randomUUID();

        List<String> main = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            main.add("slot_" + i);
        }
        List<String> armor = List.of("helmet", "chestplate", "leggings", "boots");
        InventorySnapshot<String> inv = cache.storeInventory(owner, "Alex", main, armor, "shield", 1_000L, 60);

        assertNotNull(inv.id());
        assertEquals(8, inv.id().length());
        assertEquals("Alex", inv.ownerName());
        assertEquals(SnapshotType.INV, inv.type());
        assertSame(inv, cache.get(inv.id(), 30_000L));

        List<String> chest54 = inv.toChestSlots("empty", "pane");
        assertEquals(54, chest54.size());
        // Chest slot 0 is player slot 9 (first upper storage slot); chest slot 27 is hotbar slot 0
        assertEquals("slot_9", chest54.get(0));
        assertEquals("slot_35", chest54.get(26));
        assertEquals("slot_0", chest54.get(27));
        assertEquals("slot_8", chest54.get(35));
        assertEquals("pane", chest54.get(36));
        assertEquals("pane", chest54.get(44));
        assertEquals("helmet", chest54.get(InventorySnapshot.CHEST_SLOT_HELMET));
        assertEquals("chestplate", chest54.get(InventorySnapshot.CHEST_SLOT_CHESTPLATE));
        assertEquals("leggings", chest54.get(InventorySnapshot.CHEST_SLOT_LEGGINGS));
        assertEquals("boots", chest54.get(InventorySnapshot.CHEST_SLOT_BOOTS));
        assertEquals("pane", chest54.get(49));
        assertEquals("shield", chest54.get(InventorySnapshot.CHEST_SLOT_OFFHAND));
        assertEquals("pane", chest54.get(53));

        List<String> enderSlots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            enderSlots.add("ender_" + i);
        }
        InventorySnapshot<String> ender = cache.storeEnder(owner, "Alex", enderSlots, 1_000L, 60);
        List<String> chest27 = ender.toChestSlots("empty", "pane");
        assertEquals(27, chest27.size());
        assertEquals("ender_0", chest27.get(0));
        assertEquals("ender_26", chest27.get(26));

        List<String> shulkerSlots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            shulkerSlots.add("shulker_" + i);
        }
        InventorySnapshot<String> shulker = cache.storeShulker(owner, "Red Shulker Box", shulkerSlots, 1_000L, 60);
        assertEquals(SnapshotType.SHULKER, shulker.type());
        assertEquals("Red Shulker Box", shulker.ownerName());
        List<String> shulker27 = shulker.toChestSlots("empty", "pane");
        assertEquals(27, shulker27.size());
        assertEquals("shulker_0", shulker27.get(0));
        assertEquals("shulker_26", shulker27.get(26));

        List<String> shulker36 = InventorySnapshot.toShulkerWithBackSlots(shulkerSlots, "empty", "pane", "back_arrow");
        assertEquals(36, shulker36.size());
        assertEquals("shulker_0", shulker36.get(0));
        assertEquals("shulker_26", shulker36.get(26));
        assertEquals("pane", shulker36.get(27));
        assertEquals("back_arrow", shulker36.get(InventorySnapshot.SHULKER_BACK_SLOT));
        assertEquals("pane", shulker36.get(35));
    }

    @Test
    void expiresSnapshotsAfterTtlAndEvictsOldestWhenFull() {
        SnapshotCache<String> cache = new SnapshotCache<>(16);
        UUID owner = UUID.randomUUID();
        InventorySnapshot<String> first = cache.storeEnder(owner, "Steve", List.of("a"), 1_000L, 10);
        assertEquals(1, cache.activeCount(5_000L));
        assertNotNull(cache.get(first.id(), 10_999L));
        assertNull(cache.get(first.id(), 11_000L));
        assertEquals(0, cache.activeCount(11_000L));

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(cache.storeEnder(owner, "Steve", List.of("x" + i), 20_000L + i, 300).id());
        }
        assertEquals(16, cache.activeCount(21_000L));
        assertNull(cache.get(ids.get(0), 21_000L));
        assertNotNull(cache.get(ids.get(19), 21_000L));
    }
}
