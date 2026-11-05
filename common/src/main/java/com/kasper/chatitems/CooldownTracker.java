package com.kasper.chatitems;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player anti-spam cooldown tracker for chat tokens.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> lastUsedMillis = new ConcurrentHashMap<>();

    /**
     * Returns the remaining cooldown in milliseconds for {@code playerUuid}, or {@code 0L} if ready.
     */
    public long checkRemainingMillis(UUID playerUuid, long nowMillis, int cooldownSeconds) {
        if (playerUuid == null || cooldownSeconds <= 0) {
            return 0L;
        }
        Long last = lastUsedMillis.get(playerUuid);
        if (last == null) {
            return 0L;
        }
        long elapsed = nowMillis - last;
        long cooldownMillis = cooldownSeconds * 1000L;
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    /**
     * Returns the remaining cooldown rounded up to whole seconds, or {@code 0} if ready.
     */
    public int checkRemainingSeconds(UUID playerUuid, long nowMillis, int cooldownSeconds) {
        long rem = checkRemainingMillis(playerUuid, nowMillis, cooldownSeconds);
        return rem <= 0L ? 0 : (int) ((rem + 999L) / 1000L);
    }

    /**
     * Atomically checks if {@code playerUuid} is off cooldown and records {@code nowMillis} if so.
     *
     * @return {@code true} if the player was allowed to use chat tokens now, {@code false} if on cooldown
     */
    public boolean tryAcquire(UUID playerUuid, long nowMillis, int cooldownSeconds) {
        if (playerUuid == null || cooldownSeconds <= 0) {
            if (playerUuid != null) {
                lastUsedMillis.put(playerUuid, nowMillis);
            }
            return true;
        }
        long cooldownMillis = cooldownSeconds * 1000L;
        boolean[] acquired = {false};
        lastUsedMillis.compute(playerUuid, (id, last) -> {
            if (last == null || nowMillis - last >= cooldownMillis) {
                acquired[0] = true;
                return nowMillis;
            }
            return last;
        });
        return acquired[0];
    }

    public void record(UUID playerUuid, long nowMillis) {
        if (playerUuid != null) {
            lastUsedMillis.put(playerUuid, nowMillis);
        }
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            lastUsedMillis.remove(playerUuid);
        }
    }

    public void clearAll() {
        lastUsedMillis.clear();
    }

    public void pruneExpired(long nowMillis, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            lastUsedMillis.clear();
            return;
        }
        long cutoff = nowMillis - cooldownSeconds * 1000L;
        for (Iterator<Map.Entry<UUID, Long>> it = lastUsedMillis.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() <= cutoff) {
                it.remove();
            }
        }
    }
}
