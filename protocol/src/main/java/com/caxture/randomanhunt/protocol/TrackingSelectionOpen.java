package com.caxture.randomanhunt.protocol;

import java.util.Objects;
import java.util.UUID;

public record TrackingSelectionOpen(
        int protocolVersion,
        UUID sessionId,
        long authorizationId,
        TrackingSelectionTarget currentSelection
) implements ProtocolMessage {
    public TrackingSelectionOpen {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(currentSelection, "currentSelection");
        requireAuthorization(authorizationId);
    }

    private static void requireAuthorization(long authorizationId) {
        if (authorizationId <= 0) {
            throw new IllegalArgumentException("authorizationId must be positive");
        }
    }
}
