package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.Role;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class IdentityDisplayState {
    private UUID sessionId;
    private Map<UUID, Role> roles = Map.of();
    private final Map<UUID, OriginalDisplay> originals = new LinkedHashMap<>();

    public void begin(UUID sessionId, Map<UUID, Role> roles) {
        this.sessionId = sessionId;
        this.roles = Map.copyOf(roles);
        originals.clear();
    }

    public Optional<Role> roleOf(UUID playerId) {
        return Optional.ofNullable(roles.get(playerId));
    }

    public void rememberOriginal(UUID playerId, String previousTeamName, Component listName) {
        originals.putIfAbsent(playerId, new OriginalDisplay(previousTeamName, listName));
    }

    public Map<UUID, OriginalDisplay> originals() {
        return Map.copyOf(originals);
    }

    public Map<UUID, OriginalDisplay> finish(UUID expectedSessionId) {
        if (!expectedSessionId.equals(sessionId)) {
            return Map.of();
        }
        Map<UUID, OriginalDisplay> result = Map.copyOf(originals);
        sessionId = null;
        roles = Map.of();
        originals.clear();
        return result;
    }

    public record OriginalDisplay(String previousTeamName, Component listName) {
    }
}
