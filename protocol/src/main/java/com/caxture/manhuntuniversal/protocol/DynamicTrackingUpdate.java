package com.caxture.manhuntuniversal.protocol;
import java.util.*;
public record DynamicTrackingUpdate(int protocolVersion, UUID sessionId, long authorizationId, long directoryRevision,
                                     Optional<String> currentSelection, List<TrackingTargetDescriptor> targets) implements ProtocolMessage {
    public DynamicTrackingUpdate { DynamicTrackingValidation.header(protocolVersion, sessionId, authorizationId, directoryRevision);
        targets = DynamicTrackingValidation.catalog(currentSelection, targets); }
}
