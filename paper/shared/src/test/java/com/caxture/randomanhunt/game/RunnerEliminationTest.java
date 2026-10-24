package com.caxture.randomanhunt.game;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RunnerEliminationTest {
    private final GameManager manager = new GameManager(new RoleAssigner(new Random(1)));
    private final Instant now = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void repeatedDeathCannotEliminateAnotherRunnerAndOnlyLastDeathSettles() {
        GameSession session = start(2);
        UUID first = session.runnerIds().getFirst(), last = session.runnerIds().getLast();
        assertTrue(manager.recordRunnerDeath(session.id(), first, Optional.empty(), now).isEmpty());
        assertTrue(session.isEliminatedRunner(first));
        assertEquals(List.of(last), session.livingRunnerIds());
        assertEquals(Role.RUNNER, session.roleOf(first).orElseThrow());
        assertTrue(manager.recordRunnerDeath(session.id(), first, Optional.empty(), now).isEmpty());
        assertEquals(SessionState.RUNNING, session.state());
        assertEquals(GameWinner.HUNTERS,
                manager.recordRunnerDeath(session.id(), last, Optional.empty(), now.plusSeconds(1)).orElseThrow().winner());
        assertEquals(SessionState.SETTLING, session.state());
        assertTrue(session.livingRunnerIds().isEmpty());
        assertTrue(manager.recordEnderDragonDeath(session.id(), UUID.randomUUID(), now).isEmpty());
    }

    @Test
    void eliminatedSpectatorCannotBindTargetEndButRemainingRunnerCanStillWin() {
        GameSession session = start(2);
        UUID first = session.runnerIds().getFirst(), last = session.runnerIds().getLast(), end = UUID.randomUUID();
        manager.recordRunnerDeath(session.id(), first, Optional.empty(), now);
        assertFalse(manager.bindTargetEnd(session.id(), first, end));
        assertTrue(manager.bindTargetEnd(session.id(), last, end));
        assertEquals(GameWinner.RUNNERS,
                manager.recordEnderDragonDeath(session.id(), end, now.plusSeconds(30)).orElseThrow().winner());
    }

    @Test
    void singleRunnerStillLosesOnDeathWhileHuntersAndUnknownPlayersAreIgnored() {
        GameSession session = start(1);
        assertTrue(manager.recordRunnerDeath(session.id(), session.hunterIds().getFirst(), Optional.empty(), now).isEmpty());
        assertTrue(manager.recordRunnerDeath(session.id(), UUID.randomUUID(), Optional.empty(), now).isEmpty());
        assertEquals(GameWinner.HUNTERS, manager.recordRunnerDeath(session.id(), session.runnerIds().getFirst(),
                Optional.empty(), now).orElseThrow().winner());
    }

    private GameSession start(int count) {
        List<Participant> players = List.of(new Participant(new UUID(0, 1), "A"),
                new Participant(new UUID(0, 2), "B"), new Participant(new UUID(0, 3), "C"));
        GameSession session = manager.start(players, new MatchSettings(count, false,
                new TrackingSettingsSnapshot(512, 30, 4096)), now).session().orElseThrow();
        manager.activate(session.id(), now).orElseThrow();
        return session;
    }
}
