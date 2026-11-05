package com.kasper.chatitems;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe bounded cache of immutable {@link InventorySnapshot}s with time-to-live (TTL) expiration.
 *
 * @param <S> the platform's cloned item stack representation
 */
public final class SnapshotCache<S> {

    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int ID_LENGTH = 8;

    private final Map<String, InventorySnapshot<S>> entries = new LinkedHashMap<>();
    private final int maxSnapshots;

    public SnapshotCache() {
        this(ChatItems.DEFAULT_MAX_SNAPSHOTS);
    }

    public SnapshotCache(int maxSnapshots) {
        this.maxSnapshots = Math.max(ChatItems.MIN_MAX_SNAPSHOTS, Math.min(ChatItems.MAX_MAX_SNAPSHOTS, maxSnapshots));
    }

    /**
     * Stores an immutable player inventory snapshot ({@link SnapshotType#INV}).
     */
    public synchronized InventorySnapshot<S> storeInventory(
            UUID ownerUuid,
            String ownerName,
            List<S> mainSlots,
            List<S> armorSlots,
            S offhandSlot,
            long nowMillis,
            int ttlSeconds
    ) {
        pruneExpiredLocked(nowMillis);
        evictOldestIfFullLocked();
        String id = nextUniqueIdLocked();
        long expiresAt = nowMillis + Math.max(1, ttlSeconds) * 1000L;
        InventorySnapshot<S> snapshot = new InventorySnapshot<>(
                id, ownerUuid, ownerName, SnapshotType.INV, nowMillis, expiresAt, mainSlots, armorSlots, offhandSlot);
        entries.put(id, snapshot);
        return snapshot;
    }

    /**
     * Stores an immutable ender chest snapshot ({@link SnapshotType#ENDER}).
     */
    public synchronized InventorySnapshot<S> storeEnder(
            UUID ownerUuid,
            String ownerName,
            List<S> enderSlots,
            long nowMillis,
            int ttlSeconds
    ) {
        pruneExpiredLocked(nowMillis);
        evictOldestIfFullLocked();
        String id = nextUniqueIdLocked();
        long expiresAt = nowMillis + Math.max(1, ttlSeconds) * 1000L;
        InventorySnapshot<S> snapshot = new InventorySnapshot<>(
                id, ownerUuid, ownerName, SnapshotType.ENDER, nowMillis, expiresAt, enderSlots, null, null);
        entries.put(id, snapshot);
        return snapshot;
    }

    /**
     * Looks up a snapshot by {@code id} at {@code nowMillis}. Returns {@code null} if missing or expired.
     */
    public synchronized InventorySnapshot<S> get(String id, long nowMillis) {
        if (Jv.isBlank(id)) {
            return null;
        }
        InventorySnapshot<S> snapshot = entries.get(id.trim());
        if (snapshot == null) {
            return null;
        }
        if (snapshot.isExpired(nowMillis)) {
            entries.remove(id.trim());
            return null;
        }
        return snapshot;
    }

    /**
     * Prunes expired snapshots and returns the number of active snapshots currently in cache.
     */
    public synchronized int activeCount(long nowMillis) {
        pruneExpiredLocked(nowMillis);
        return entries.size();
    }

    /**
     * Returns the most recently stored non-expired snapshot of {@code type}, or {@code null} if none.
     */
    public synchronized InventorySnapshot<S> latestOfType(SnapshotType type, long nowMillis) {
        pruneExpiredLocked(nowMillis);
        InventorySnapshot<S> latest = null;
        for (InventorySnapshot<S> s : entries.values()) {
            if (s.type() == type) {
                latest = s;
            }
        }
        return latest;
    }

    public synchronized void clear() {
        entries.clear();
    }

    private void pruneExpiredLocked(long nowMillis) {
        for (Iterator<Map.Entry<String, InventorySnapshot<S>>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isExpired(nowMillis)) {
                it.remove();
            }
        }
    }

    private void evictOldestIfFullLocked() {
        while (entries.size() >= maxSnapshots && !entries.isEmpty()) {
            Iterator<String> it = entries.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    private String nextUniqueIdLocked() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 64; attempt++) {
            StringBuilder b = new StringBuilder(ID_LENGTH);
            for (int i = 0; i < ID_LENGTH; i++) {
                b.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
            }
            String id = b.toString();
            if (!entries.containsKey(id)) {
                return id;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, ID_LENGTH);
    }
}
