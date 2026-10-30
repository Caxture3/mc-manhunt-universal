package com.caxture.manhuntuniversal.game;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionGameStateTest {
    private static final Instant START = Instant.parse("2026-10-25T00:00:00Z");
    private final GameManager manager = new GameManager(new RoleAssigner(new Random(29)));

    @Test
    void assignmentsRequireTheCompleteRosterAndBothLivingTeamsWithoutPartialChanges() {
        GameSession session = running(2);
        Map<UUID, Role> before = roles(session);
        long revision = session.revision();
        Map<UUID, Role> missing = new LinkedHashMap<>(before);
        missing.remove(session.participants().getFirst().id());
        Map<UUID, Role> extra = new LinkedHashMap<>(before);
        extra.put(new UUID(9, 9), Role.HUNTER);

        for (Map<UUID, Role> invalid : List.of(missing, extra,
                assigned(session, List.of()), assigned(session, ids(session)))) {
            assertFalse(manager.assignRoles(session.id(), invalid, START.plusSeconds(1)));
            assertEquals(before, roles(session));
            assertEquals(revision, session.revision());
            assertEquals(SessionState.RUNNING, session.state());
            assertTrue(session.roundHistory().isEmpty());
        }
    }

    @Test
    void countdownAndOldSessionRequestsCannotChangeTheCurrentMatch() {
        GameSession previous = countdown(1);
        Map<UUID, Role> before = roles(previous);
        long countdownRevision = previous.revision();
        assertFalse(manager.assignRoles(previous.id(), assigned(previous, previous.hunterIds()), START));
        assertEquals(before, roles(previous));
        assertEquals(countdownRevision, previous.revision());
        manager.cancel(previous.id(), TerminationReason.ADMIN_STOPPED, START).orElseThrow();
        manager.clearSettled(previous.id()).orElseThrow();

        GameSession current = running(2);
        Map<UUID, Role> currentRoles = roles(current);
        long revision = current.revision();
        UUID player = current.runnerIds().getFirst();
        assertFalse(manager.assignRoles(previous.id(), assigned(current, current.hunterIds()), START));
        assertFalse(manager.reviveParticipant(previous.id(), player));
        assertTrue(manager.eliminateParticipant(previous.id(), player, Optional.empty(), START).isEmpty());
        assertTrue(manager.finishCustom(previous.id(), GameWinner.RUNNERS, "obsolete timer", START).isEmpty());
        assertEquals(currentRoles, roles(current));
        assertFalse(current.isEliminatedParticipant(player));
        assertEquals(revision, current.revision());
        assertEquals(SessionState.RUNNING, current.state());
    }

    @Test
    void roleChangesPreserveEliminationAndRejectChangingAnEliminatedPlayersTeam() {
        GameSession session = running(2);
        UUID eliminated = session.runnerIds().getFirst();
        UUID livingRunner = session.runnerIds().getLast();
        UUID hunter = session.hunterIds().getFirst();
        manager.recordRunnerDeath(session.id(), eliminated, Optional.empty(), START.plusSeconds(1));
        Map<UUID, Role> before = roles(session);
        long revision = session.revision();
        Map<UUID, Role> invalid = new LinkedHashMap<>(before);
        invalid.put(eliminated, Role.HUNTER);
        invalid.put(hunter, Role.RUNNER);
        assertFalse(manager.assignRoles(session.id(), invalid, START.plusSeconds(2)));
        assertEquals(before, roles(session));
        assertEquals(revision, session.revision());

        Map<UUID, Role> valid = new LinkedHashMap<>(before);
        valid.put(livingRunner, Role.HUNTER);
        valid.put(hunter, Role.RUNNER);
        assertTrue(manager.assignRoles(session.id(), valid, START.plusSeconds(3)));
        assertTrue(session.isEliminatedParticipant(eliminated));
        assertTrue(session.isEliminatedRunner(eliminated));
        assertEquals(Optional.of(Role.RUNNER), session.roleOf(eliminated));
        assertEquals(List.of(hunter), session.livingRunnerIds());
        assertFalse(session.isRunnerAlive(eliminated));
    }

    @Test
    void anEliminatedRunnerCannotSatisfyTheLivingRunnerRequirement() {
        GameSession session = running(2);
        UUID eliminated = session.runnerIds().getFirst();
        manager.recordRunnerDeath(session.id(), eliminated, Optional.empty(), START);
        Map<UUID, Role> before = roles(session);
        long revision = session.revision();
        assertFalse(manager.assignRoles(session.id(), assigned(session, List.of(eliminated)), START));
        assertEquals(before, roles(session));
        assertEquals(revision, session.revision());
    }

    @Test
    void anEliminatedHunterCannotSatisfyTheLivingHunterRequirement() {
        GameSession session = running(2);
        UUID eliminated = session.hunterIds().getFirst();
        manager.eliminateParticipant(session.id(), eliminated, Optional.empty(), START);
        Map<UUID, Role> before = roles(session);
        long revision = session.revision();
        List<UUID> runners = ids(session).stream().filter(id -> !id.equals(eliminated)).toList();
        assertFalse(manager.assignRoles(session.id(), assigned(session, runners), START));
        assertEquals(before, roles(session));
        assertEquals(revision, session.revision());
    }

    @Test
    void anEliminatedHuntersRoleIsAlsoFixedDuringAssignments() {
        GameSession session = running(2);
        UUID eliminated = session.hunterIds().getFirst();
        manager.eliminateParticipant(session.id(), eliminated, Optional.empty(), START);
        Map<UUID, Role> before = roles(session);
        Map<UUID, Role> invalid = new LinkedHashMap<>(before);
        invalid.put(eliminated, Role.RUNNER);
        invalid.put(session.runnerIds().getFirst(), Role.HUNTER);
        long revision = session.revision();
        assertFalse(manager.assignRoles(session.id(), invalid, START));
        assertEquals(before, roles(session));
        assertTrue(session.isEliminatedParticipant(eliminated));
        assertEquals(revision, session.revision());
    }

    @Test
    void reviveRestoresOnlyTheRequestedParticipantAndKeepsTheirTeam() {
        GameSession session = running(2);
        UUID runner = session.runnerIds().getFirst();
        UUID hunter = session.hunterIds().getFirst();
        assertTrue(manager.eliminateParticipant(session.id(), hunter, Optional.empty(), START).isEmpty());
        assertTrue(manager.eliminateParticipant(session.id(), runner, Optional.empty(), START).isEmpty());
        assertTrue(session.isEliminatedParticipant(hunter));
        assertFalse(session.livingHunterIds().contains(hunter));
        assertFalse(session.isEliminatedRunner(hunter));
        assertTrue(manager.reviveParticipant(session.id(), hunter));
        assertEquals(Optional.of(Role.HUNTER), session.roleOf(hunter));
        assertTrue(session.livingHunterIds().contains(hunter));
        assertTrue(session.isEliminatedParticipant(runner));
        assertTrue(manager.reviveParticipant(session.id(), runner));
        assertTrue(session.isRunnerAlive(runner));
        assertEquals(Optional.of(Role.RUNNER), session.roleOf(runner));

        long revision = session.revision();
        assertFalse(manager.reviveParticipant(session.id(), runner));
        assertFalse(manager.reviveParticipant(session.id(), new UUID(8, 8)));
        assertTrue(manager.eliminateParticipant(session.id(), new UUID(8, 8), Optional.empty(), START).isEmpty());
        assertEquals(revision, session.revision());
    }

    @Test
    void revisionsAdvanceForCommittedChangesAndStayStableForRejectedOrRepeatedRequests() {
        GameSession session = countdown(2);
        long revision = session.revision();
        manager.activate(session.id(), START).orElseThrow();
        assertTrue(session.revision() > revision);
        revision = session.revision();
        assertTrue(manager.activate(session.id(), START).isEmpty());
        assertEquals(revision, session.revision());

        UUID runner = session.runnerIds().getFirst();
        UUID end = new UUID(7, 7);
        assertTrue(manager.bindTargetEnd(session.id(), runner, end));
        assertTrue(session.revision() > revision);
        revision = session.revision();
        assertFalse(manager.bindTargetEnd(session.id(), runner, end));
        assertEquals(revision, session.revision());

        manager.recordRunnerDeath(session.id(), runner, Optional.empty(), START.plusSeconds(1));
        assertTrue(session.revision() > revision);
        revision = session.revision();
        manager.recordRunnerDeath(session.id(), runner, Optional.empty(), START.plusSeconds(2));
        assertEquals(revision, session.revision());
        assertTrue(manager.reviveParticipant(session.id(), runner));
        assertTrue(session.revision() > revision);
        revision = session.revision();
        assertTrue(manager.assignRoles(session.id(), assigned(session, session.hunterIds()), START.plusSeconds(3)));
        assertTrue(session.revision() > revision);
        revision = session.revision();
        manager.finishCustom(session.id(), GameWinner.RUNNERS, "checkpoint", START.plusSeconds(4)).orElseThrow();
        assertTrue(session.revision() > revision);
    }

    @Test
    void randomSelectionCanChooseNoneOrAllCandidatesWithoutMutatingTheMatch() {
        GameSession session = running(2);
        Map<UUID, Role> before = roles(session);
        long revision = session.revision();
        List<UUID> candidates = session.hunterIds();
        assertTrue(manager.randomRunners(candidates, 0).isEmpty());
        assertEquals(candidates, manager.randomRunners(candidates, candidates.size()));
        assertTrue(manager.randomRunners(List.of(), 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> manager.randomRunners(candidates, -1));
        assertThrows(IllegalArgumentException.class, () -> manager.randomRunners(candidates, candidates.size() + 1));
        assertEquals(before, roles(session));
        assertEquals(revision, session.revision());
    }

    @Test
    void explicitFullTeamRerollPreservesTheBoundEndAndOriginalStartTime() {
        GameSession session = running(2);
        UUID end = new UUID(6, 6);
        List<UUID> formerRunners = List.copyOf(session.runnerIds());
        List<UUID> formerHunters = List.copyOf(session.hunterIds());
        assertTrue(manager.bindTargetEnd(session.id(), formerRunners.getFirst(), end));
        List<UUID> rerolled = manager.randomRunners(formerHunters, formerHunters.size());
        assertTrue(manager.assignRoles(session.id(), assigned(session, rerolled), START.plusSeconds(30)));
        assertEquals(formerHunters, session.runnerIds());
        assertEquals(formerRunners, session.hunterIds());
        assertEquals(Optional.of(end), session.targetEndWorldId());
        assertEquals(Optional.of(START), session.startedAt());
        assertEquals(4, session.participants().size());
        assertEquals(GameWinner.RUNNERS,
                manager.recordEnderDragonDeath(session.id(), end, START.plusSeconds(60)).orElseThrow().winner());
    }

    @Test
    void withoutAnExtensionRolesStayFixedThroughDeathsAndEndBinding() {
        GameSession session = running(2);
        Map<UUID, Role> before = roles(session);
        UUID dead = session.runnerIds().getFirst();
        manager.recordRunnerDeath(session.id(), dead, Optional.empty(), START.plusSeconds(1));
        manager.bindTargetEnd(session.id(), session.livingRunnerIds().getFirst(), new UUID(5, 5));
        manager.recordRunnerDeath(session.id(), session.hunterIds().getFirst(), Optional.empty(), START.plusSeconds(2));
        assertEquals(before, roles(session));
        assertEquals(SessionState.RUNNING, session.state());
        assertTrue(session.roundHistory().isEmpty());
    }

    @Test
    void deferredVictoryKeepsTheSessionRunningAndAllowsRevivingTheLastRunner() {
        GameSession session = running(1);
        UUID runner = session.runnerIds().getFirst();
        AtomicInteger decisions = new AtomicInteger();
        manager.onVictoryDecision((current, proposed) -> {
            decisions.incrementAndGet();
            assertSame(session, current);
            assertEquals(SessionState.RUNNING, current.state());
            assertTrue(current.roundHistory().isEmpty());
            assertTrue(current.isEliminatedParticipant(runner));
            assertEquals(GameWinner.HUNTERS, proposed.winner());
            return Optional.empty();
        });

        assertTrue(manager.recordRunnerDeath(session.id(), runner, Optional.empty(), START.plusSeconds(1)).isEmpty());
        assertEquals(1, decisions.get());
        assertEquals(SessionState.RUNNING, session.state());
        assertTrue(session.livingRunnerIds().isEmpty());
        assertTrue(session.roundHistory().isEmpty());
        assertTrue(manager.reviveParticipant(session.id(), runner));
        assertTrue(session.isRunnerAlive(runner));
        assertEquals(SessionState.RUNNING, session.state());
    }

    @Test
    void victoryReplacementChangesWinnerAndCustomReasonBeforeSettlement() {
        GameSession session = running(1);
        manager.onVictoryDecision((current, proposed) -> {
            assertEquals(SessionState.RUNNING, current.state());
            assertTrue(current.roundHistory().isEmpty());
            return Optional.of(new GameResult(proposed.sessionId(), GameWinner.RUNNERS, GameResultReason.CUSTOM,
                    proposed.settledAt(), proposed.participants(), proposed.runners(), proposed.hunters(),
                    proposed.roundHistory(), proposed.deadRunner(), proposed.killer(), "survival objective"));
        });

        GameResult result = manager.recordRunnerDeath(session.id(), session.runnerIds().getFirst(),
                Optional.empty(), START.plusSeconds(1)).orElseThrow();
        assertEquals(GameWinner.RUNNERS, result.winner());
        assertEquals(GameResultReason.CUSTOM, result.reason());
        assertEquals("survival objective", result.customReason());
        assertEquals(session.id(), result.sessionId());
        assertEquals(SessionState.SETTLING, session.state());
        assertEquals(1, session.roundHistory().size());
        assertTrue(manager.recordRunnerDeath(session.id(), session.runnerIds().getFirst(),
                Optional.empty(), START.plusSeconds(2)).isEmpty());
    }

    @Test
    void explicitCustomFinishPassesThroughThePrecommitHookAndCannotCommitTwice() {
        GameSession session = running(1);
        AtomicInteger decisions = new AtomicInteger();
        manager.onVictoryDecision((current, proposed) -> {
            decisions.incrementAndGet();
            assertEquals(SessionState.RUNNING, current.state());
            assertEquals(GameResultReason.CUSTOM, proposed.reason());
            assertEquals("time limit reached", proposed.customReason());
            return Optional.of(proposed);
        });
        GameResult result = manager.finishCustom(session.id(), GameWinner.HUNTERS,
                "time limit reached", START.plusSeconds(120)).orElseThrow();
        assertEquals(GameWinner.HUNTERS, result.winner());
        assertEquals(GameResultReason.CUSTOM, result.reason());
        assertEquals("time limit reached", result.customReason());
        assertEquals(1, decisions.get());
        assertEquals(SessionState.SETTLING, session.state());
        long revision = session.revision();
        assertTrue(manager.finishCustom(session.id(), GameWinner.RUNNERS, "duplicate", START).isEmpty());
        assertFalse(manager.reviveParticipant(session.id(), session.runnerIds().getFirst()));
        assertEquals(revision, session.revision());
        assertEquals(1, session.roundHistory().size());
    }

    @Test
    void aCancelledCustomFinishDoesNotSettleOrChangeTheRevision() {
        GameSession session = running(1);
        long revision = session.revision();
        manager.onVictoryDecision((current, proposed) -> Optional.empty());
        assertTrue(manager.finishCustom(session.id(), GameWinner.RUNNERS, "cancelled", START).isEmpty());
        assertEquals(SessionState.RUNNING, session.state());
        assertEquals(revision, session.revision());
        assertTrue(session.roundHistory().isEmpty());
    }

    private GameSession running(int runners) {
        GameSession session = countdown(runners);
        manager.activate(session.id(), START).orElseThrow();
        return session;
    }

    private GameSession countdown(int runners) {
        List<Participant> players = List.of(new Participant(new UUID(0, 1), "One"),
                new Participant(new UUID(0, 2), "Two"), new Participant(new UUID(0, 3), "Three"),
                new Participant(new UUID(0, 4), "Four"));
        return manager.start(players, new MatchSettings(runners, false,
                new TrackingSettingsSnapshot(512, 30, 4096)), START).session().orElseThrow();
    }

    private static List<UUID> ids(GameSession session) {
        return session.participants().stream().map(Participant::id).toList();
    }

    private static Map<UUID, Role> roles(GameSession session) {
        Map<UUID, Role> result = new LinkedHashMap<>();
        ids(session).forEach(id -> result.put(id, session.roleOf(id).orElseThrow()));
        return result;
    }

    private static Map<UUID, Role> assigned(GameSession session, List<UUID> runners) {
        Map<UUID, Role> result = new LinkedHashMap<>();
        ids(session).forEach(id -> result.put(id, runners.contains(id) ? Role.RUNNER : Role.HUNTER));
        return result;
    }
}
