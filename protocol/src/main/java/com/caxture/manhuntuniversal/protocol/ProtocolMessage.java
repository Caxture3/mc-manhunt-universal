package com.caxture.manhuntuniversal.protocol;

public sealed interface ProtocolMessage permits ClientHello, ServerHello,
        TrackingSelectionOpen, TrackingSelectionChoose, TrackingSelectionClose,
        TrackingSelectionUpdate, DynamicTrackingOpen, DynamicTrackingUpdate, DynamicTrackingChoose {
    int protocolVersion();
}
