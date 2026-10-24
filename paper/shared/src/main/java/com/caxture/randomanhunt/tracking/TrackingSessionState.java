package com.caxture.randomanhunt.tracking;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TrackingSessionState {
    private final UUID sessionId;
    private final Map<UUID, TrackingTargetId> runnerSelections = new HashMap<>();
    private final Map<UUID, Map<UUID, TrackingPosition>> positions = new HashMap<>();
    private final Map<UUID, TrackingPosition> latestPositions = new HashMap<>();
    private final Map<UUID, Map<TrackingWorldKind, UUID>> recentWorlds = new HashMap<>();
    private final Map<UUID, TrackingSearchToken> currentSearches = new HashMap<>();
    private long nextVersion;

    public TrackingSessionState(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public TrackingTargetId toggleRunnerSelection(UUID playerId, TrackingTargetId requested) {
        TrackingTargetId next = runnerSelection(playerId) == requested
                ? TrackingTargetId.NONE : requested;
        return setRunnerSelection(playerId, next);
    }

    public TrackingTargetId setRunnerSelection(UUID playerId, TrackingTargetId selection) {
        if (selection == TrackingTargetId.NONE) {
            runnerSelections.remove(playerId);
        } else {
            runnerSelections.put(playerId, selection);
        }
        return selection;
    }

    public TrackingTargetId runnerSelection(UUID playerId) {
        return runnerSelections.getOrDefault(playerId, TrackingTargetId.NONE);
    }

    public void recordPosition(UUID playerId, TrackingPosition position) {
        positions.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .put(position.worldId(), position);
        latestPositions.put(playerId, position);
        recentWorlds.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(position.worldKind(), position.worldId());
    }

    public Optional<TrackingPosition> latestPosition(UUID playerId) {
        return Optional.ofNullable(latestPositions.get(playerId));
    }

    public Optional<UUID> recentWorld(UUID playerId, TrackingWorldKind worldKind) {
        return Optional.ofNullable(recentWorlds.get(playerId)).map(worlds -> worlds.get(worldKind));
    }

    public Optional<TrackingPosition> lastPosition(UUID playerId, UUID worldId) {
        return Optional.ofNullable(positions.get(playerId)).map(byWorld -> byWorld.get(worldId));
    }

    public TrackingSearchToken newSearch(UUID playerId, TrackingTargetId target, UUID worldId) {
        TrackingSearchToken token = new TrackingSearchToken(
                sessionId, playerId, target, worldId, ++nextVersion);
        currentSearches.put(playerId, token);
        return token;
    }

    public boolean isCurrent(TrackingSearchToken token) {
        return token.sessionId().equals(sessionId)
                && token.equals(currentSearches.get(token.playerId()));
    }

    public void invalidate(UUID playerId) {
        currentSearches.remove(playerId);
    }
}
