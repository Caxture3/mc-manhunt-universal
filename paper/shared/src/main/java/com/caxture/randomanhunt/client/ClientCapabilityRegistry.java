package com.caxture.randomanhunt.client;

import com.caxture.randomanhunt.protocol.ClientHello;
import com.caxture.randomanhunt.protocol.ProtocolConstants;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClientCapabilityRegistry {
    private final Set<UUID> compatiblePlayers = new HashSet<>();

    public boolean accept(UUID playerId, ClientHello hello) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(hello, "hello");
        boolean compatible = hello.protocolVersion() == ProtocolConstants.VERSION;
        if (compatible) {
            compatiblePlayers.add(playerId);
        } else {
            compatiblePlayers.remove(playerId);
        }
        return compatible;
    }

    public boolean supports(UUID playerId) {
        return compatiblePlayers.contains(playerId);
    }

    public void disconnect(UUID playerId) {
        compatiblePlayers.remove(playerId);
    }

    public void clear() {
        compatiblePlayers.clear();
    }
}
