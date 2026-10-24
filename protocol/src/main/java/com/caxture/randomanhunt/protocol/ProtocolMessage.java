package com.caxture.randomanhunt.protocol;

public sealed interface ProtocolMessage permits ClientHello, ServerHello,
        TrackingSelectionOpen, TrackingSelectionChoose, TrackingSelectionClose {
    int protocolVersion();
}
