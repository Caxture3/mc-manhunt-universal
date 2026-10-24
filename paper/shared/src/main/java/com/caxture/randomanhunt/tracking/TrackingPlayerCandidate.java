package com.caxture.randomanhunt.tracking;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TrackingPlayerCandidate(
        UUID playerId,
        String name,
        boolean online,
        Optional<TrackingPosition> actualPosition,
        Optional<TrackingPosition> lastPositionInViewerWorld
) {
    public TrackingPlayerCandidate {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(actualPosition, "actualPosition");
        Objects.requireNonNull(lastPositionInViewerWorld, "lastPositionInViewerWorld");
    }
}
