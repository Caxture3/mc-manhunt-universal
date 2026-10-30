package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TrackingDialogWindowTest {
    @Test
    void cachedClicksReadTheUpdatedSelectionAndAvailability() {
        List<TrackingTargetId> selections = new ArrayList<>();
        TrackingDialogWindow<Long> window = new TrackingDialogWindow<>(
                TrackingTargetId.NONE, TrackingAvailability.allUnlocked(), selections::add, () -> fail());
        long revision = window.actions(0, value -> value);
        window.update(TrackingTargetId.VILLAGE, new TrackingAvailability(1_000, 0, -1));

        window.select(revision, TrackingTargetId.VILLAGE);
        window.select(revision, TrackingTargetId.BASTION_REMNANT);
        assertTrue(selections.isEmpty(), "A formerly enabled action must obey the latest lock/visibility");

        window.update(TrackingTargetId.VILLAGE, TrackingAvailability.allUnlocked());
        window.select(revision, TrackingTargetId.VILLAGE);
        assertEquals(List.of(TrackingTargetId.NONE), selections);
        assertFalse(window.active());
    }

    @Test
    void dismissClosesOnlyTheWindowAndLaterUpdatesCannotReviveIt() {
        AtomicInteger dismissals = new AtomicInteger();
        List<TrackingTargetId> selections = new ArrayList<>();
        TrackingDialogWindow<Long> window = new TrackingDialogWindow<>(TrackingTargetId.VILLAGE,
                TrackingAvailability.allUnlocked(), selections::add, dismissals::incrementAndGet);
        long revision = window.actions(0, value -> value);

        window.dismiss(revision);
        window.dismiss(revision);
        assertFalse(window.update(TrackingTargetId.NONE, TrackingAvailability.allUnlocked()));
        window.select(revision, TrackingTargetId.NETHER_FORTRESS);
        assertEquals(1, dismissals.get());
        assertTrue(selections.isEmpty(), "Dismissing must preserve the actual tracking selection");
    }

    @Test
    void refreshReusesCallbacksAndAnEscapeDuringRotationStillDismissesTheWindow() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger dismissals = new AtomicInteger();
        TrackingDialogWindow<Long> window = new TrackingDialogWindow<>(TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> fail(), dismissals::incrementAndGet);
        long first = window.actions(0, value -> { registrations.incrementAndGet(); return value; });
        for (int second = 1; second < 240; second++) {
            long action = window.actions(Duration.ofSeconds(second).toNanos(),
                    value -> { registrations.incrementAndGet(); return value; });
            assertEquals(first, action);
        }
        long rotated = window.actions(Duration.ofMinutes(4).toNanos(),
                value -> { registrations.incrementAndGet(); return value; });
        assertEquals(2, registrations.get(), "Refresh must not retain hundreds of callback registrations");
        assertNotEquals(first, rotated);
        window.dismiss(first);
        assertFalse(window.active(), "Esc from the rendering being replaced must still dismiss the same window");
        assertEquals(1, dismissals.get());
        window.dismiss(rotated);
        assertEquals(1, dismissals.get());
    }

    @Test
    void expiredChoiceActionsCannotSelectAfterTheirGenerationRotates() {
        List<TrackingTargetId> selections = new ArrayList<>();
        TrackingDialogWindow<Long> window = new TrackingDialogWindow<>(TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), selections::add, () -> fail());
        long first = window.actions(0, value -> value);
        long rotated = window.actions(Duration.ofMinutes(4).toNanos(), value -> value);
        window.select(first, TrackingTargetId.VILLAGE);
        assertTrue(selections.isEmpty());
        window.select(rotated, TrackingTargetId.NETHER_FORTRESS);
        assertEquals(List.of(TrackingTargetId.NETHER_FORTRESS), selections);
    }

    @Test
    void serverCloseInvalidatesCallbacksWithoutCallingDismiss() {
        TrackingDialogWindow<Long> window = new TrackingDialogWindow<>(TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> fail(), () -> fail());
        long revision = window.actions(0, value -> value);
        window.close();
        window.select(revision, TrackingTargetId.VILLAGE);
        window.dismiss(revision);
        assertFalse(window.active());
    }
}
