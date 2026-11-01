package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Requires exact match and per-viewer control revisions, preventing late task writes. @since 1.2 */
public record TrackingControlMutation(TrackingMutation tracking, long expectedControlRevision) {
    public TrackingControlMutation { Objects.requireNonNull(tracking); if(expectedControlRevision<0 || tracking.context().expectedRevision()<0)
        throw new IllegalArgumentException("Exact match and control revisions required"); }
}
