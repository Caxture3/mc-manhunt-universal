package com.caxture.manhuntuniversal.protocol;

public sealed interface ProtocolMessage permits ClientHello, ServerHello,
        TrackingSelectionOpen, TrackingSelectionChoose, TrackingSelectionClose,
        TrackingSelectionUpdate {
    int protocolVersion();
}
