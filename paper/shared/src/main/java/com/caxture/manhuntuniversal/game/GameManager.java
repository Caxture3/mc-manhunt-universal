package com.caxture.manhuntuniversal.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GameManager {
    private final RoleAssigner roleAssigner;
    private GameSession activeSession;
    private final TeamSelectionService teamSelections = new TeamSelectionService(() -> activeSession != null);

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
        if (!isRunning(expectedSessionId) || !activeSession.eliminateRunner(playerId)) {
            return Optional.empty();
        }
        if (!activeSession.livingRunnerIds().isEmpty()) {
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
        if (reason != TerminationReason.ADMIN_STOPPED && reason != TerminationReason.PLUGIN_DISABLED) {
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
        if (activeSession == null || activeSession.state() != SessionState.RUNNING) {
            return Optional.empty();
        }
        activeSession.settle(at, terminationReason);
        return Optional.of(new GameResult(
                activeSession.id(),
                winner,
                resultReason,
                at,
                activeSession.participants(),
                activeSession.runnerIds(),
                activeSession.hunterIds(),
                activeSession.roundHistory(),
                deadRunner,
                killer));
    }

    private boolean matches(UUID expectedSessionId) {
        return activeSession != null && activeSession.id().equals(expectedSessionId);
    }

    private boolean isRunning(UUID expectedSessionId) {
        return matches(expectedSessionId) && activeSession.state() == SessionState.RUNNING;
    }
}
