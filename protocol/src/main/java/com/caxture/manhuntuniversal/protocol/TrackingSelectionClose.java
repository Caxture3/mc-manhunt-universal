package com.caxture.manhuntuniversal.protocol;

import java.util.Objects;
import java.util.UUID;

public record TrackingSelectionClose(
        int protocolVersion,
        UUID sessionId,
        long authorizationId
) implements ProtocolMessage {
    public TrackingSelectionClose {
        Objects.requireNonNull(sessionId, "sessionId");
        if (authorizationId <= 0) {
            throw new IllegalArgumentException("authorizationId must be positive");
        }
    }
}
