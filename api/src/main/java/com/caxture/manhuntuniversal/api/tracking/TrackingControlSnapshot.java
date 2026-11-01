package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Control revision is independent of match/catalog revisions. Player ID is fixed target, not selector result. @since 1.2 */
public record TrackingControlSnapshot(UUID sessionId, UUID playerId, long revision, TrackingOverrideMode mode,
                                      Optional<String> targetId, Optional<UUID> fixedPlayerId, Optional<String> ownerId) {
    public TrackingControlSnapshot { Objects.requireNonNull(sessionId);Objects.requireNonNull(playerId);Objects.requireNonNull(mode);
        Objects.requireNonNull(targetId);Objects.requireNonNull(fixedPlayerId);Objects.requireNonNull(ownerId);if(revision<0)throw new IllegalArgumentException("Invalid revision"); }
}
