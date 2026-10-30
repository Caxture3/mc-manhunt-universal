package com.caxture.manhuntuniversal.protocol;

import java.util.Objects;
import java.util.UUID;

/** Updates an already open selection for the matching session and authorization. */
public record TrackingSelectionUpdate(
        int protocolVersion,
        UUID sessionId,
        long authorizationId,
        TrackingSelectionTarget currentSelection,
        TrackingAvailability availability
) implements ProtocolMessage {
    public TrackingSelectionUpdate {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(currentSelection, "currentSelection");
        Objects.requireNonNull(availability, "availability");
        if (authorizationId <= 0) {
            throw new IllegalArgumentException("authorizationId must be positive");
        }
    }
}
