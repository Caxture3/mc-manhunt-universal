package com.caxture.randomanhunt.tracking;

import java.util.Objects;

public record HunterTrackingTarget(
        TrackingPlayerCandidate player,
        TrackingPosition direction,
        boolean liveSameWorld
) {
    public HunterTrackingTarget {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(direction, "direction");
    }
}
