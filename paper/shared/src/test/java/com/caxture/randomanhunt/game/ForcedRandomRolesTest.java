package com.caxture.randomanhunt.game;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ForcedRandomRolesTest {
    @Test
    void enablingClearsChoicesRejectsEveryChoiceAndDisablingDoesNotRestoreThem() {
        TeamSelectionService selections = new TeamSelectionService(() -> false);
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        selections.choose(a, TeamPreference.RUNNER, 1);
        selections.choose(b, TeamPreference.HUNTER, 1);
        selections.setForcedRandom(true);
        assertTrue(selections.forcedRandom());
        assertTrue(selections.snapshot().isEmpty());
        for (TeamPreference choice : TeamPreference.values()) {
            assertEquals(TeamSelectionService.ChoiceResult.DISABLED, selections.choose(a, choice, 1));
        }
        selections.setForcedRandom(false);
        assertTrue(selections.snapshot().isEmpty());
        assertEquals(TeamSelectionService.ChoiceResult.CHANGED, selections.choose(a, TeamPreference.RUNNER, 1));
    }

    @Test
    void enablingDuringCountdownClearsReservationsWithoutChangingLockedOrRunningRoles() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(0)));
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        manager.teamSelections().choose(a, TeamPreference.RUNNER, 1);
        manager.teamSelections().choose(b, TeamPreference.HUNTER, 1);
        GameSession session = manager.start(List.of(new Participant(a, "A"), new Participant(b, "B"),
                        new Participant(c, "C")),
                new MatchSettings(1, false, new TrackingSettingsSnapshot(512, 30, 4096)), Instant.EPOCH)
                .session().orElseThrow();
        manager.teamSelections().setForcedRandom(true);
        assertTrue(manager.teamSelections().snapshot().isEmpty());
        assertEquals(List.of(a), session.runnerIds());
        manager.activate(session.id(), Instant.EPOCH);
        manager.teamSelections().setForcedRandom(false);
        assertEquals(List.of(a), session.runnerIds());
        assertEquals(TeamSelectionService.ChoiceResult.LOCKED,
                manager.teamSelections().choose(b, TeamPreference.RUNNER, 1));
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, Instant.EPOCH);
        manager.clearSettled(session.id());
        manager.teamSelections().setForcedRandom(true);
        assertTrue(manager.start(List.of(new Participant(a, "A"), new Participant(b, "B")),
                session.settings(), Instant.EPOCH).session().isPresent());
    }
}
