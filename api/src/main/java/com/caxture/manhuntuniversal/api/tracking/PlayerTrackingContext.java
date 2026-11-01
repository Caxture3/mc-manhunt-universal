package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Immutable live selector input; eliminated and position-unknown players are excluded. @since 1.2 */
public record PlayerTrackingContext(TrackingViewerContext viewer, List<PlayerTrackingCandidate> candidates) {
    public PlayerTrackingContext { Objects.requireNonNull(viewer); candidates=List.copyOf(candidates); }
}
