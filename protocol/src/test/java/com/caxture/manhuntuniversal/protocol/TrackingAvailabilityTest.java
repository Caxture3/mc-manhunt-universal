package com.caxture.manhuntuniversal.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackingAvailabilityTest {
    @Test
    void distinguishesDisabledLockedAndUnlockedStructures() {
        var state = new TrackingAvailability(-1, 1, 0);
        assertEquals(-1, state.remainingMillis(TrackingSelectionTarget.VILLAGE));
        assertFalse(state.visible(TrackingSelectionTarget.VILLAGE));
        assertFalse(state.unlocked(TrackingSelectionTarget.VILLAGE));
        assertEquals(1, state.remainingMillis(TrackingSelectionTarget.NETHER_FORTRESS));
        assertTrue(state.visible(TrackingSelectionTarget.NETHER_FORTRESS));
        assertFalse(state.unlocked(TrackingSelectionTarget.NETHER_FORTRESS));
        assertEquals(0, state.remainingMillis(TrackingSelectionTarget.BASTION_REMNANT));
        assertTrue(state.visible(TrackingSelectionTarget.BASTION_REMNANT));
        assertTrue(state.unlocked(TrackingSelectionTarget.BASTION_REMNANT));
        assertTrue(state.hasVisibleOptions());
    }

    @Test
    void noneIsNeverAVisibleOrUnlockedOption() {
        var state = TrackingAvailability.allUnlocked();
        assertEquals(-1, state.remainingMillis(TrackingSelectionTarget.NONE));
        assertFalse(state.visible(TrackingSelectionTarget.NONE));
        assertFalse(state.unlocked(TrackingSelectionTarget.NONE));
        for (var target : TrackingSelectionTarget.values()) {
            if (target.selectable()) {
                assertTrue(state.unlocked(target));
            }
        }
        assertFalse(new TrackingAvailability(-1, -1, -1).hasVisibleOptions());
    }

    @Test
    void rejectsValuesBelowDisabledSentinelInEveryField() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingAvailability(-2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackingAvailability(0, -2, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackingAvailability(0, 0, -2));
    }
}
