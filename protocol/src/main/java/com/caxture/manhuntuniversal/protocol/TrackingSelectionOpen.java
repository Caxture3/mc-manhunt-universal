package com.caxture.manhuntuniversal.protocol;

import java.util.Objects;
import java.util.UUID;

public record TrackingSelectionOpen(
        int protocolVersion,
        UUID sessionId,
        long authorizationId,
        TrackingSelectionTarget currentSelection,
        TrackingAvailability availability
) implements ProtocolMessage {
    public TrackingSelectionOpen {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(currentSelection, "currentSelection");
        Objects.requireNonNull(availability, "availability");
        requireAuthorization(authorizationId);
    }

    public TrackingSelectionOpen(int protocolVersion, UUID sessionId, long authorizationId,
                                 TrackingSelectionTarget currentSelection) {
        this(protocolVersion, sessionId, authorizationId, currentSelection,
                TrackingAvailability.allUnlocked());
    }

    private static void requireAuthorization(long authorizationId) {
        if (authorizationId <= 0) {
            throw new IllegalArgumentException("authorizationId must be positive");
        }
    }
}
