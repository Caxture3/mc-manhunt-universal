package com.caxture.randomanhunt.optimization;

import com.caxture.randomanhunt.game.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HunterEndAccessPolicyTest {
    @Test
    void onlyCurrentHuntersInRunningEnabledGameAreBlockedUntilLivingRunnerActuallyEnters() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(1)));
        UUID a = new UUID(0, 1), b = new UUID(0, 2), hunter = new UUID(0, 3);
        manager.teamSelections().choose(a, TeamPreference.RUNNER, 2);
        manager.teamSelections().choose(b, TeamPreference.RUNNER, 2);
        GameSession session = manager.start(List.of(new Participant(a, "A"), new Participant(b, "B"),
                        new Participant(hunter, "Hunter")),
                new MatchSettings(2, false, new TrackingSettingsSnapshot(512, 30, 4096)), Instant.EPOCH)
                .session().orElseThrow();
        assertFalse(HunterEndAccessPolicy.blocked(session, hunter, true));
        manager.activate(session.id(), Instant.EPOCH);
        assertFalse(HunterEndAccessPolicy.blocked(session, hunter, false));
        assertFalse(HunterEndAccessPolicy.blocked(session, a, true));
        assertFalse(HunterEndAccessPolicy.blocked(session, UUID.randomUUID(), true));
        assertTrue(HunterEndAccessPolicy.blocked(session, hunter, true));
        manager.recordRunnerDeath(session.id(), a, Optional.empty(), Instant.EPOCH);
        assertFalse(manager.bindTargetEnd(session.id(), a, UUID.randomUUID()));
        assertTrue(HunterEndAccessPolicy.blocked(session, hunter, true));
        assertTrue(manager.bindTargetEnd(session.id(), b, UUID.randomUUID()));
        assertFalse(HunterEndAccessPolicy.blocked(session, hunter, true));
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, Instant.EPOCH);
        assertFalse(HunterEndAccessPolicy.blocked(session, hunter, true));
        assertFalse(HunterEndAccessPolicy.blocked(null, hunter, true));
    }
}
