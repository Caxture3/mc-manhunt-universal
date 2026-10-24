package com.caxture.randomanhunt.tracking;

import java.util.UUID;

public final class TrackingCompassIdentity {
    public enum Kind { ORDINARY, CURRENT, STALE }

    private TrackingCompassIdentity() {
    }

    public static Kind classify(
            boolean marked,
            String storedSession,
            String storedKind,
            UUID activeSession,
            TrackingCompassKind expectedKind
    ) {
        if (!marked) {
            return Kind.ORDINARY;
        }
        return activeSession != null
                && activeSession.toString().equals(storedSession)
                && expectedKind != null
                && TrackingCompassKind.fromId(storedKind).filter(expectedKind::equals).isPresent()
                ? Kind.CURRENT : Kind.STALE;
    }
}
