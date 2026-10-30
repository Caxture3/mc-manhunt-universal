package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureUnlockAnnouncementsTest {
    @Test void announcesOnlyPositiveCountdownsThatReachZeroAndOnlyOnce() {
        var announcements = new StructureUnlockAnnouncements();
        announcements.begin(UUID.randomUUID(), new TrackingAvailability(1_000, 2_000, -1));
        assertEquals(List.of(), announcements.advance(new TrackingAvailability(1, 1_001, -1)));
        assertEquals(List.of(TrackingTargetId.VILLAGE),
                announcements.advance(new TrackingAvailability(0, 1_000, -1)));
        assertEquals(List.of(), announcements.advance(new TrackingAvailability(0, 1, -1)));
        assertEquals(List.of(TrackingTargetId.NETHER_FORTRESS),
                announcements.advance(new TrackingAvailability(0, 0, -1)));
        assertEquals(List.of(), announcements.advance(new TrackingAvailability(0, 0, -1)));
    }

    @Test void immediateAndDisabledTargetsDoNotAnnounceAtStartOrWhenEnabled() {
        var announcements = new StructureUnlockAnnouncements();
        announcements.begin(UUID.randomUUID(), new TrackingAvailability(0, -1, 0));
        assertEquals(List.of(), announcements.advance(new TrackingAvailability(0, -1, 0)));
        assertEquals(List.of(), announcements.advance(TrackingAvailability.allUnlocked()));
    }

    @Test void restartingTrackingForSameMatchDoesNotRepeatAnnouncements() {
        var announcements = new StructureUnlockAnnouncements();
        UUID match = UUID.randomUUID();
        var initial = new TrackingAvailability(1_000, -1, -1);
        announcements.begin(match, initial);
        assertEquals(List.of(TrackingTargetId.VILLAGE),
                announcements.advance(new TrackingAvailability(0, -1, -1)));
        announcements.begin(match, initial);
        assertEquals(List.of(), announcements.advance(new TrackingAvailability(0, -1, -1)));
    }

    @Test void newMatchAndConfigurationRelockCanAnnounceNewCountdownCompletion() {
        var announcements = new StructureUnlockAnnouncements();
        var locked = new TrackingAvailability(1_000, -1, -1);
        var unlocked = new TrackingAvailability(0, -1, -1);
        announcements.begin(UUID.randomUUID(), locked);
        assertEquals(List.of(TrackingTargetId.VILLAGE), announcements.advance(unlocked));
        assertEquals(List.of(), announcements.advance(locked));
        assertEquals(List.of(TrackingTargetId.VILLAGE), announcements.advance(unlocked));
        announcements.begin(UUID.randomUUID(), locked);
        assertEquals(List.of(TrackingTargetId.VILLAGE), announcements.advance(unlocked));
    }

    @Test void disablingPendingTargetDoesNotAnnounceAndSkippedTicksStillCatchCompletion() {
        var announcements = new StructureUnlockAnnouncements();
        announcements.begin(UUID.randomUUID(), new TrackingAvailability(1_000, 1_000, 1_000));
        assertEquals(List.of(TrackingTargetId.NETHER_FORTRESS, TrackingTargetId.BASTION_REMNANT),
                announcements.advance(new TrackingAvailability(-1, 0, 0)));
    }
}
