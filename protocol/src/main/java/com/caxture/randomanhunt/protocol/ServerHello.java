package com.caxture.randomanhunt.protocol;

public record ServerHello(int protocolVersion, boolean compatible) implements ProtocolMessage {
}
