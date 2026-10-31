package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.*;
import java.util.*;
/** Viewer is actual position; origin is in the target world and may use dimension conversion. */
public record LocateRequest(UUID sessionId, long targetRevision, long requestVersion, UUID playerId, String targetId,
                            WorldPosition viewer, WorldPosition origin, Optional<MatchSnapshot> match, int radiusBlocks) {
    public LocateRequest { Objects.requireNonNull(sessionId); Objects.requireNonNull(playerId); Objects.requireNonNull(targetId);
        Objects.requireNonNull(viewer); Objects.requireNonNull(origin); Objects.requireNonNull(match);
        if (targetRevision < 0 || requestVersion < 0 || radiusBlocks < 1) throw new IllegalArgumentException("Invalid locate request"); }
}
