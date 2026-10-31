package com.caxture.manhuntuniversal.protocol;
import java.util.*;
public record DynamicTrackingChoose(int protocolVersion, UUID sessionId, long authorizationId, long directoryRevision,
                                     String targetId) implements ProtocolMessage {
    public DynamicTrackingChoose { DynamicTrackingValidation.header(protocolVersion, sessionId, authorizationId, directoryRevision); DynamicTrackingValidation.id(targetId); }
}
