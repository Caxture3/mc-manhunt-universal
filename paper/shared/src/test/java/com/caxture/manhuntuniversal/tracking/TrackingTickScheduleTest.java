package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackingTickScheduleTest {
    @Test void tenthSecondDisplayNeverMultipliesOneSecondMaintenance() {
        var schedule = new TrackingTickSchedule();
        int displays = 0, maintenance = 0;
        for (int i = 0; i < 20; i++) {
            var step = schedule.advance(2);
            if (step.display()) displays++;
            if (step.maintenance()) maintenance++;
        }
        assertEquals(10, displays);
        assertEquals(1, maintenance);
    }
    @Test void defaultIsOneSecondAndLongerIntervalsStillMaintainUnlocksEverySecond() {
        var schedule = new TrackingTickSchedule();
        for (int i = 0; i < 19; i++) assertFalse(schedule.advance(20).display());
        assertTrue(schedule.advance(20).display());
        int maintenance = 0, keepAlive = 0;
        for (int i = 0; i < 60; i++) {
            var step = schedule.advance(100);
            assertFalse(step.display());
            if (step.maintenance()) maintenance++;
            if (step.keepAlive()) keepAlive++;
        }
        assertEquals(3, maintenance);
        assertEquals(3, keepAlive, "Long distance intervals must keep the ActionBar visible without recalculation");
    }
    @Test void shorteningTheLiveIntervalTakesEffectAndForcedDisplaysDoNotDelayMaintenance() {
        var schedule = new TrackingTickSchedule();
        for (int i = 0; i < 8; i++) schedule.advance(20);
        assertTrue(schedule.advance(2).display());
        schedule.displayed();
        int maintenance = 0;
        for (int i = 0; i < 11; i++) if (schedule.advance(2).maintenance()) maintenance++;
        assertEquals(1, maintenance);
        schedule.clear();
        assertFalse(schedule.advance(2).display());
    }
}
