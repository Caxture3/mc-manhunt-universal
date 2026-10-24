package com.caxture.randomanhunt.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GameSession {
    private final UUID id;
    private final Instant createdAt;
    private final List<Participant> participants;
    private final Map<UUID, Participant> participantsById;
    private MatchSettings settings;
    private final List<RunnerRound> roundHistory = new ArrayList<>();
    private final List<UUID> runnerIds;
    private final Set<UUID> eliminatedRunners = new LinkedHashSet<>();
    private SessionState state = SessionState.COUNTDOWN;
    private Instant startedAt;
    private Instant currentRoundStartedAt;
    private UUID targetEndWorldId;

    GameSession(
            UUID id,
            Instant createdAt,
            List<Participant> participants,
            MatchSettings settings,
            List<UUID> runnerIds
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.participants = List.copyOf(participants);
        this.settings = settings;
        this.runnerIds = List.copyOf(runnerIds);
        LinkedHashMap<UUID, Participant> indexed = new LinkedHashMap<>();
        participants.forEach(participant -> indexed.put(participant.id(), participant));
        this.participantsById = Map.copyOf(indexed);
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<Participant> participants() {
        return participants;
    }

    public Optional<Participant> participant(UUID playerId) {
        return Optional.ofNullable(participantsById.get(playerId));
    }

    public MatchSettings settings() {
        return settings;
    }

    public SessionState state() {
        return state;
    }

    public List<UUID> runnerIds() {
        return runnerIds;
    }

    public List<UUID> hunterIds() {
        return participants.stream().map(Participant::id).filter(id -> !runnerIds.contains(id)).toList();
    }

    public List<UUID> livingRunnerIds() {
        return runnerIds.stream().filter(id -> !eliminatedRunners.contains(id)).toList();
    }

    public boolean isEliminatedRunner(UUID playerId) {
        return eliminatedRunners.contains(playerId);
    }

    public boolean isRunnerAlive(UUID playerId) {
        return runnerIds.contains(playerId) && !eliminatedRunners.contains(playerId);
    }

    boolean eliminateRunner(UUID playerId) {
        return runnerIds.contains(playerId) && eliminatedRunners.add(playerId);
    }

    public Optional<Role> roleOf(UUID playerId) {
        if (!participantsById.containsKey(playerId)) {
            return Optional.empty();
        }
        return Optional.of(runnerIds.contains(playerId) ? Role.RUNNER : Role.HUNTER);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<UUID> targetEndWorldId() {
        return Optional.ofNullable(targetEndWorldId);
    }

    public List<RunnerRound> roundHistory() {
        return List.copyOf(roundHistory);
    }

    void activate(Instant at) {
        state = SessionState.RUNNING;
        startedAt = at;
        currentRoundStartedAt = at;
    }

    void refreshTrackingSettings(TrackingSettingsSnapshot trackingSettings) {
        settings = new MatchSettings(
                settings.runnerCount(),
                settings.resetPlayersAtStart(),
                trackingSettings);
    }

    boolean bindTargetEnd(UUID worldId) {
        if (targetEndWorldId != null) {
            return false;
        }
        targetEndWorldId = worldId;
        return true;
    }

    void settle(Instant at, TerminationReason reason) {
        if (state == SessionState.RUNNING) {
            closeCurrentRound(at, reason);
        }
        state = SessionState.SETTLING;
    }

    private void closeCurrentRound(Instant at, TerminationReason reason) {
        roundHistory.add(new RunnerRound(runnerIds, currentRoundStartedAt, at, reason));
    }
}
