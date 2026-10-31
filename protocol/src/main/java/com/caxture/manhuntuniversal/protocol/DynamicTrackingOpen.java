package com.caxture.manhuntuniversal.protocol;
import java.util.*;
public record DynamicTrackingOpen(int protocolVersion, UUID sessionId, long authorizationId, long directoryRevision,
                                   Optional<String> currentSelection, List<TrackingTargetDescriptor> targets) implements ProtocolMessage {
    public DynamicTrackingOpen { DynamicTrackingValidation.header(protocolVersion, sessionId, authorizationId, directoryRevision);
        targets = DynamicTrackingValidation.catalog(currentSelection, targets); }
}
