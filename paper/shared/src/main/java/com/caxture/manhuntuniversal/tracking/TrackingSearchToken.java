package com.caxture.manhuntuniversal.tracking;

import java.util.Objects;
import java.util.UUID;

public record TrackingSearchToken(
        UUID sessionId,
        UUID playerId,
        TrackingTargetId target,
        UUID worldId,
        long version
) {
    public TrackingSearchToken {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(worldId, "worldId");
    }
}
