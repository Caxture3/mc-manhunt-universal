package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrackingSearchQueueTest {
    @Test void changingStructureInvalidatesOnlyStaticJobsAndKeepsPendingPortalLookup() {
        var queue = new TrackingSearchQueue();
        UUID player = UUID.randomUUID();
        List<String> completed = new ArrayList<>();
        var structure = queue.replace(player, TrackingSearchQueue.Lane.STATIC);
        queue.add(structure, () -> completed.add("old-structure"));
        var portal = queue.replace(player, TrackingSearchQueue.Lane.PORTAL);
        queue.add(portal, () -> completed.add("portal"));
        queue.invalidate(player, TrackingSearchQueue.Lane.STATIC);
        var replacement = queue.replace(player, TrackingSearchQueue.Lane.STATIC);
        queue.add(replacement, () -> completed.add("new-structure"));
        assertEquals(2, queue.pendingCount());
        assertTrue(queue.runOne());
        assertEquals(List.of("portal"), completed);
        assertTrue(queue.runOne());
        assertEquals(List.of("portal", "new-structure"), completed);
        assertFalse(queue.runOne());
    }

    @Test void fullInvalidationStillCancelsBothLanesWithoutAffectingOtherPlayers() {
        var queue = new TrackingSearchQueue();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        List<String> completed = new ArrayList<>();
        for (var lane : TrackingSearchQueue.Lane.values()) {
            queue.add(queue.replace(player, lane), () -> completed.add("stale"));
        }
        queue.add(queue.replace(otherPlayer, TrackingSearchQueue.Lane.PORTAL), () -> completed.add("other"));
        queue.invalidate(player);
        assertEquals(1, queue.pendingCount());
        assertTrue(queue.runOne());
        assertEquals(List.of("other"), completed);
        assertFalse(queue.runOne());
    }
}
