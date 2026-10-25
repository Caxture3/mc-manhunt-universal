package com.caxture.manhuntuniversal.game;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedRolesTest {
    private static final Instant START = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void initialDrawIsKeptForTheWholeMatchAndRecordedOnce() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(17)));
        GameSession session = start(manager, 2);
        List<UUID> runners = List.copyOf(session.runnerIds());
        assertEquals(2, runners.size());
        assertEquals(2, session.hunterIds().size());
        manager.activate(session.id(), START).orElseThrow();
        assertEquals(runners, session.runnerIds());
        assertThrows(UnsupportedOperationException.class, () -> session.runnerIds().clear());
        assertTrue(manager.recordRunnerDeath(session.id(), runners.getFirst(),
                Optional.empty(), START.plus(Duration.ofDays(10))).isEmpty());
        assertEquals(SessionState.RUNNING, session.state());
        GameResult result = manager.recordRunnerDeath(session.id(), runners.getLast(),
                Optional.empty(), START.plus(Duration.ofDays(10)).plusSeconds(1)).orElseThrow();
        assertEquals(runners, result.runners());
        assertEquals(1, result.roundHistory().size());
        assertEquals(runners, result.roundHistory().getFirst().runners());
        assertTrue(manager.recordRunnerDeath(session.id(), runners.getLast(),
                Optional.empty(), START.plusSeconds(864_001)).isEmpty());
    }

    @Test
    void noRotationOperationRemainsExposed() {
        assertTrue(Arrays.stream(GameManager.class.getMethods()).noneMatch(method ->
                method.getName().equals("tick") || method.getName().equals("reshuffle")));
        assertTrue(Arrays.stream(MatchSettings.class.getRecordComponents()).noneMatch(component ->
                component.getName().startsWith("switch")));
    }

    @Test
    void hunterDeathAndOtherEndDoNotSettleButTargetDragonDoes() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(2)));
        GameSession session = start(manager, 1);
        manager.activate(session.id(), START).orElseThrow();
        assertTrue(manager.recordRunnerDeath(session.id(), session.hunterIds().getFirst(),
                Optional.empty(), START.plusSeconds(30)).isEmpty());
        UUID end = UUID.randomUUID();
        assertFalse(manager.bindTargetEnd(session.id(), session.hunterIds().getFirst(), end));
        assertTrue(manager.bindTargetEnd(session.id(), session.runnerIds().getFirst(), end));
        assertTrue(manager.recordEnderDragonDeath(session.id(), UUID.randomUUID(), START).isEmpty());
        assertEquals(GameWinner.RUNNERS,
                manager.recordEnderDragonDeath(session.id(), end, START.plusSeconds(500)).orElseThrow().winner());
    }

    @Test
    void cancelledCountdownHasNoRoundAndCanBeClearedForNextMatch() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(3)));
        GameSession session = start(manager, 1);
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, START).orElseThrow();
        assertEquals(SessionState.SETTLING, session.state());
        assertTrue(session.roundHistory().isEmpty());
        manager.clearSettled(session.id()).orElseThrow();
        assertTrue(manager.activeSession().isEmpty());
        assertNotEquals(session.id(), start(manager, 1).id());
    }

    private static GameSession start(GameManager manager, int runners) {
        return manager.start(List.of(
                new Participant(new UUID(0, 1), "One"),
                new Participant(new UUID(0, 2), "Two"),
                new Participant(new UUID(0, 3), "Three"),
                new Participant(new UUID(0, 4), "Four")),
                new MatchSettings(runners, false, new TrackingSettingsSnapshot(512, 30, 4096)),
                START).session().orElseThrow();
    }
}
