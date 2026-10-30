package com.caxture.manhuntuniversal.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.function.BiFunction;

public final class GameManager {
    private final RoleAssigner roleAssigner;
    private GameSession activeSession;
    private final TeamSelectionService teamSelections = new TeamSelectionService(() -> activeSession != null);
    private BiFunction<GameSession, GameResult, Optional<GameResult>> victoryDecision = (session, result) -> Optional.of(result);

    public GameManager(RoleAssigner roleAssigner) {
        this.roleAssigner = roleAssigner;
    }

    public synchronized StartResult start(
            List<Participant> requestedParticipants,
            MatchSettings settings,
            Instant createdAt
    ) {
        if (activeSession != null) {
            return StartResult.rejected(StartStatus.ALREADY_RUNNING);
        }
        Map<UUID, Participant> unique = new LinkedHashMap<>();
        requestedParticipants.forEach(participant -> unique.putIfAbsent(participant.id(), participant));
        List<Participant> participants = List.copyOf(unique.values());
        if (participants.size() <= settings.runnerCount()) {
            return StartResult.rejected(StartStatus.NOT_ENOUGH_PLAYERS);
        }
        List<UUID> ids = participants.stream().map(Participant::id).toList();
        Map<UUID, TeamPreference> preferences = teamSelections.snapshot();
        long selectedRunners = ids.stream().filter(id -> preferences.get(id) == TeamPreference.RUNNER).count();
        if (selectedRunners > settings.runnerCount()) {
            return StartResult.rejected(StartStatus.RUNNER_SELECTION_LIMIT_EXCEEDED);
        }
        long randomPlayers = ids.stream().filter(id ->
                preferences.getOrDefault(id, TeamPreference.RANDOM) == TeamPreference.RANDOM).count();
        int missing = settings.runnerCount() - (int) selectedRunners - (int) randomPlayers;
        if (missing > 0) {
            return StartResult.missingRandomPlayers(missing);
        }
        List<UUID> runners = roleAssigner.select(ids, settings.runnerCount(), preferences);
        activeSession = new GameSession(
                UUID.randomUUID(), createdAt, participants, settings, runners);
        return StartResult.started(activeSession);
    }

    public synchronized Optional<GameSession> activeSession() {
        return Optional.ofNullable(activeSession);
    }

    public TeamSelectionService teamSelections() {
        return teamSelections;
    }

    public synchronized void onVictoryDecision(BiFunction<GameSession, GameResult, Optional<GameResult>> decision) {
        victoryDecision = Objects.requireNonNull(decision);
    }

    public synchronized boolean assignRoles(UUID expectedSessionId, Map<UUID, Role> assignment, Instant at) {
        if (!isRunning(expectedSessionId) || assignment == null || at == null) return false;
        var ids = activeSession.participants().stream().map(Participant::id).toList();
        if (!assignment.keySet().equals(new java.util.HashSet<>(ids)) || assignment.values().stream().anyMatch(Objects::isNull)) return false;
        for (UUID id : ids) {
            if (activeSession.isEliminatedParticipant(id) && assignment.get(id) != activeSession.roleOf(id).orElseThrow()) return false;
        }
        long livingRunners = ids.stream().filter(id -> !activeSession.isEliminatedParticipant(id)
                && assignment.get(id) == Role.RUNNER).count();
        long livingHunters = ids.stream().filter(id -> !activeSession.isEliminatedParticipant(id)
                && assignment.get(id) == Role.HUNTER).count();
        if (livingRunners == 0 || livingHunters == 0) return false;
        activeSession.assignRunners(ids.stream().filter(id -> assignment.get(id) == Role.RUNNER).toList(), at);
        return true;
    }

    public synchronized List<UUID> randomRunners(List<UUID> candidates, int runnerCount) {
        List<UUID> stable = List.copyOf(candidates);
        if (stable.stream().distinct().count() != stable.size() || runnerCount < 0 || runnerCount > stable.size()) {
            throw new IllegalArgumentException("Invalid random candidates or runner count");
        }
        if (runnerCount == 0) return List.of();
        if (runnerCount == stable.size()) return stable;
        return roleAssigner.select(stable, runnerCount);
    }

    public synchronized boolean reviveParticipant(UUID expectedSessionId, UUID playerId) {
        return isRunning(expectedSessionId) && activeSession.reviveParticipant(playerId);
    }

    public synchronized Optional<GameResult> finishCustom(UUID expectedSessionId, GameWinner winner, String reason, Instant at) {
        if (!isRunning(expectedSessionId) || winner == null || reason == null || reason.isBlank()) return Optional.empty();
        return settle(winner, GameResultReason.CUSTOM, TerminationReason.EXTENSION_FINISHED, at,
                Optional.empty(), Optional.empty(), reason);
    }

    public synchronized Optional<GameSession> activate(UUID expectedSessionId, Instant at) {
        if (!matches(expectedSessionId)) {
            return Optional.empty();
        }
        return activate(expectedSessionId, at, activeSession.settings().trackingSettings());
    }

    public synchronized Optional<GameSession> activate(
            UUID expectedSessionId,
            Instant at,
            TrackingSettingsSnapshot trackingSettings
    ) {
        if (!matches(expectedSessionId) || activeSession.state() != SessionState.COUNTDOWN) {
            return Optional.empty();
        }
        activeSession.refreshTrackingSettings(trackingSettings);
        activeSession.activate(at);
        return Optional.of(activeSession);
    }

    public synchronized boolean bindTargetEnd(
            UUID expectedSessionId,
            UUID playerId,
            UUID worldId
    ) {
        return isRunning(expectedSessionId)
                && activeSession.isRunnerAlive(playerId)
                && activeSession.bindTargetEnd(worldId);
    }

    public synchronized Optional<GameResult> recordRunnerDeath(
            UUID expectedSessionId,
            UUID playerId,
            Optional<UUID> killer,
            Instant at
    ) {
        if (!isRunning(expectedSessionId) || !activeSession.isRunnerAlive(playerId)) {
            return Optional.empty();
        }
        return eliminateParticipant(expectedSessionId, playerId, killer, at);
    }

    public synchronized Optional<GameResult> eliminateParticipant(UUID expectedSessionId, UUID playerId,
                                                                 Optional<UUID> killer, Instant at) {
        if (!isRunning(expectedSessionId) || !activeSession.eliminateParticipant(playerId)) return Optional.empty();
        if (activeSession.roleOf(playerId).orElseThrow() != Role.RUNNER || !activeSession.livingRunnerIds().isEmpty()) {
            return Optional.empty();
        }
        return settle(
                GameWinner.HUNTERS,
                GameResultReason.RUNNER_DIED,
                TerminationReason.RUNNER_DIED,
                at,
                Optional.of(playerId),
                killer);
    }

    public synchronized Optional<GameResult> recordEnderDragonDeath(
            UUID expectedSessionId,
            UUID worldId,
            Instant at
    ) {
        return settleTargetEnd(
                expectedSessionId,
                worldId,
                GameResultReason.ENDER_DRAGON_DEFEATED,
                TerminationReason.ENDER_DRAGON_DEFEATED,
                at);
    }

    public synchronized Optional<GameResult> recordEnderDragonAbsent(
            UUID expectedSessionId,
            UUID worldId,
            Instant at
    ) {
        return settleTargetEnd(
                expectedSessionId,
                worldId,
                GameResultReason.ENDER_DRAGON_ABSENT,
                TerminationReason.ENDER_DRAGON_ABSENT,
                at);
    }

    public synchronized Optional<GameSession> cancel(
            UUID expectedSessionId,
            TerminationReason reason,
            Instant at
    ) {
        if (!matches(expectedSessionId) || activeSession.state() == SessionState.SETTLING) {
            return Optional.empty();
        }
        if (reason != TerminationReason.ADMIN_STOPPED && reason != TerminationReason.PLUGIN_DISABLED
                && reason != TerminationReason.START_CANCELLED) {
            throw new IllegalArgumentException("Cancellation requires a stop reason");
        }
        activeSession.settle(at, reason);
        return Optional.of(activeSession);
    }

    public synchronized Optional<GameSession> clearSettled(UUID expectedSessionId) {
        if (!matches(expectedSessionId) || activeSession.state() != SessionState.SETTLING) {
            return Optional.empty();
        }
        GameSession cleared = activeSession;
        activeSession = null;
        teamSelections.clear();
        return Optional.of(cleared);
    }

    private Optional<GameResult> settleTargetEnd(
            UUID expectedSessionId,
            UUID worldId,
            GameResultReason resultReason,
            TerminationReason terminationReason,
            Instant at
    ) {
        if (!isRunning(expectedSessionId)
                || !activeSession.targetEndWorldId().filter(worldId::equals).isPresent()) {
            return Optional.empty();
        }
        return settle(
                GameWinner.RUNNERS,
                resultReason,
                terminationReason,
                at,
                Optional.empty(),
                Optional.empty());
    }

    private Optional<GameResult> settle(
            GameWinner winner,
            GameResultReason resultReason,
            TerminationReason terminationReason,
            Instant at,
            Optional<UUID> deadRunner,
            Optional<UUID> killer
    ) {
        return settle(winner, resultReason, terminationReason, at, deadRunner, killer, "");
    }

    private Optional<GameResult> settle(GameWinner winner, GameResultReason resultReason,
                                       TerminationReason terminationReason, Instant at,
                                       Optional<UUID> deadRunner, Optional<UUID> killer, String customReason) {
        if (activeSession == null || activeSession.state() != SessionState.RUNNING) {
            return Optional.empty();
        }
        GameSession candidateSession = activeSession;
        long revision = candidateSession.revision();
        GameResult candidate = new GameResult(candidateSession.id(), winner, resultReason, at,
                candidateSession.participants(), candidateSession.runnerIds(), candidateSession.hunterIds(),
                candidateSession.roundHistory(), deadRunner, killer, customReason);
        Optional<GameResult> decided = victoryDecision.apply(candidateSession, candidate);
        if (decided.isEmpty() || activeSession != candidateSession || candidateSession.revision() != revision
                || candidateSession.state() != SessionState.RUNNING) return Optional.empty();
        GameResult chosen = decided.orElseThrow();
        if (!candidateSession.id().equals(chosen.sessionId())) return Optional.empty();
        candidateSession.settle(at, terminationReason);
        return Optional.of(new GameResult(candidateSession.id(), chosen.winner(), chosen.reason(), at,
                candidateSession.participants(), candidateSession.runnerIds(), candidateSession.hunterIds(),
                candidateSession.roundHistory(), chosen.deadRunner(), chosen.killer(), chosen.customReason()));
    }

    private boolean matches(UUID expectedSessionId) {
        return activeSession != null && activeSession.id().equals(expectedSessionId);
    }

    private boolean isRunning(UUID expectedSessionId) {
        return matches(expectedSessionId) && activeSession.state() == SessionState.RUNNING;
    }
}
