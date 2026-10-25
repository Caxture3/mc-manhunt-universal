package com.caxture.manhuntuniversal.protocol;

import java.util.Objects;
import java.util.UUID;

public record TrackingSelectionChoose(
        int protocolVersion,
        UUID sessionId,
        long authorizationId,
        TrackingSelectionTarget selection
) implements ProtocolMessage {
    public TrackingSelectionChoose {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(selection, "selection");
        if (authorizationId <= 0) {
            throw new IllegalArgumentException("authorizationId must be positive");
        }
    }
}
