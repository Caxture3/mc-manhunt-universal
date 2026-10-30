package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.config.RunnerStructureUnlockTimes;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class StructureUnlockPolicyTest {
    private static final Instant START = Instant.parse("2026-10-31T00:00:00Z");

    @Test void disabledImmediateAndDelayedTargetsHaveIndependentStates() {
        var times = new RunnerStructureUnlockTimes(-1, 900, 1200);
        var initial = StructureUnlockPolicy.availability(times, START, START);
        assertFalse(initial.visible(TrackingSelectionTarget.VILLAGE));
        assertEquals(900_000, initial.remainingMillis(TrackingSelectionTarget.BASTION_REMNANT));
        assertEquals(1_200_000, initial.remainingMillis(TrackingSelectionTarget.NETHER_FORTRESS));
        var later = StructureUnlockPolicy.availability(times, START, START.plusSeconds(900));
        assertTrue(later.unlocked(TrackingSelectionTarget.BASTION_REMNANT));
        assertFalse(later.unlocked(TrackingSelectionTarget.NETHER_FORTRESS));
        assertTrue(StructureUnlockPolicy.availability(times, START, START.plusSeconds(1200))
                .unlocked(TrackingSelectionTarget.NETHER_FORTRESS));
    }

    @Test void justBeforeDeadlineIsLockedAndExactDeadlineIsAvailable() {
        var times = new RunnerStructureUnlockTimes(120, 0, -1);
        var before = StructureUnlockPolicy.availability(times, START, START.plusMillis(119_999));
        assertEquals(1, before.remainingMillis(TrackingSelectionTarget.VILLAGE));
        assertTrue(before.unlocked(TrackingSelectionTarget.BASTION_REMNANT));
        assertTrue(StructureUnlockPolicy.availability(times, START, START.plusSeconds(120))
                .unlocked(TrackingSelectionTarget.VILLAGE));
    }

    @Test void allDisabledDiffersFromAllPendingAndConfigurationChangesUseOriginalStart() {
        assertFalse(new RunnerStructureUnlockTimes(-1, -1, -1).anyEnabled());
        assertTrue(new RunnerStructureUnlockTimes(120, 900, 1200).anyEnabled());
        assertTrue(StructureUnlockPolicy.availability(new RunnerStructureUnlockTimes(120, 900, 1200),
                START, START.plusSeconds(180)).unlocked(TrackingSelectionTarget.VILLAGE));
        assertEquals(120_000, StructureUnlockPolicy.availability(new RunnerStructureUnlockTimes(300, 900, 1200),
                START, START.plusSeconds(180)).remainingMillis(TrackingSelectionTarget.VILLAGE));
    }

    @Test void defaultsPreserveImmediateAccessAndLongDurationsDoNotOverflow() {
        var defaults = new RunnerStructureUnlockTimes(0, 0, 0);
        assertTrue(StructureUnlockPolicy.availability(defaults, START, START).unlocked(TrackingSelectionTarget.VILLAGE));
        assertEquals(120_000, StructureUnlockPolicy.availability(new RunnerStructureUnlockTimes(120, 0, 0),
                START, START.minusSeconds(60)).remainingMillis(TrackingSelectionTarget.VILLAGE));
        assertEquals(2_147_483_647_000L, StructureUnlockPolicy.availability(
                new RunnerStructureUnlockTimes(Integer.MAX_VALUE, 0, 0), START, START)
                .remainingMillis(TrackingSelectionTarget.VILLAGE));
        assertThrows(IllegalArgumentException.class, () -> new RunnerStructureUnlockTimes(-2, 0, 0));
    }

    @Test void submissionsCannotSelectHiddenOrLockedTargetsButCanClearAnExistingSelection() {
        var available = StructureUnlockPolicy.availability(new RunnerStructureUnlockTimes(-1, 120, 0), START, START);
        assertFalse(StructureUnlockPolicy.allows(TrackingTargetId.VILLAGE, available));
        assertFalse(StructureUnlockPolicy.allows(TrackingTargetId.BASTION_REMNANT, available));
        assertTrue(StructureUnlockPolicy.allows(TrackingTargetId.NETHER_FORTRESS, available));
        assertTrue(StructureUnlockPolicy.allows(TrackingTargetId.NONE, available));
        assertFalse(StructureUnlockPolicy.allows(null, available));
    }
}
