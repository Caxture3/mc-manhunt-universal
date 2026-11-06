package com.caxture.manhuntuniversal.player;

import com.caxture.manhuntuniversal.game.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HunterStartCountdownDisplayTest {
    @Test void secondsChangeAtTheDeadlineBoundariesEvenWhenTicksDoNotAlign() {
        var games = new GameManager(new RoleAssigner(new Random(1)));
        games.start(List.of(new Participant(new UUID(0, 1), "Runner"),
                new Participant(new UUID(0, 2), "Hunter")),
                new MatchSettings(1, true, new TrackingSettingsSnapshot(512, 30, 4096)), Instant.EPOCH);
        var session = games.activeSession().orElseThrow();
        games.activate(session.id(), Instant.EPOCH);
        var display = new HunterStartCountdownDisplay();
        var updates = new ArrayList<String>();
        // The first callback is halfway through a server tick, then ticks take 50ms.
        // The old 20-tick refresh sees 3 at 975ms and leaves 1 visible for only 25ms.
        for (int tick = 0; tick <= 60; tick++) {
            long millis = tick == 0 ? 0 : 25 + (tick - 1) * 50L;
            long seconds = HunterStartWaitPolicy.remaining(session, 3, Instant.EPOCH.plusMillis(millis));
            if (display.update(seconds)) updates.add(millis + ":" + seconds);
        }
        assertEquals(List.of("0:3", "1025:2", "2025:1"), updates);
        assertEquals(0, HunterStartWaitPolicy.remaining(session, 3, Instant.EPOCH.plusSeconds(3)));
    }

    @Test void additionalSyncsAndSkippedSecondsDoNotDelayTheNextUpdateAndResetForcesARefresh() {
        var display = new HunterStartCountdownDisplay();
        assertTrue(display.update(30));
        assertFalse(display.update(30));
        assertFalse(display.update(30));
        assertTrue(display.update(29));
        assertFalse(display.update(29));
        assertTrue(display.update(26));
        display.reset();
        assertTrue(display.update(26));
    }
}
