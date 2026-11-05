package com.kasper.chatitems;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTrackerTest {

    @Test
    void enforcesAndExpiresCooldown() {
        CooldownTracker tracker = new CooldownTracker();
        UUID player = UUID.randomUUID();
        long t0 = 100_000L;

        assertEquals(0, tracker.checkRemainingSeconds(player, t0, 3));
        assertTrue(tracker.tryAcquire(player, t0, 3));

        // 500 ms later: 2500 ms left -> rounds up to 3 seconds
        assertFalse(tracker.tryAcquire(player, t0 + 500L, 3));
        assertEquals(2500L, tracker.checkRemainingMillis(player, t0 + 500L, 3));
        assertEquals(3, tracker.checkRemainingSeconds(player, t0 + 500L, 3));

        // 2100 ms later: 900 ms left -> rounds up to 1 second
        assertEquals(1, tracker.checkRemainingSeconds(player, t0 + 2100L, 3));

        // 3000 ms later: ready again
        assertEquals(0, tracker.checkRemainingSeconds(player, t0 + 3000L, 3));
        assertTrue(tracker.tryAcquire(player, t0 + 3000L, 3));
    }

    @Test
    void zeroCooldownAlwaysAllows() {
        CooldownTracker tracker = new CooldownTracker();
        UUID player = UUID.randomUUID();
        assertTrue(tracker.tryAcquire(player, 1000L, 0));
        assertTrue(tracker.tryAcquire(player, 1001L, 0));
        assertEquals(0, tracker.checkRemainingSeconds(player, 1001L, 0));
    }
}
