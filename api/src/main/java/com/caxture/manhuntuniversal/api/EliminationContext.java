package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable policy input before a participant is eliminated; API requests use cause API.
 * @param match detached immutable match snapshot
 * @param playerId affected participant identifier
 * @param cause elimination trigger; API for explicit requests
 */
public record EliminationContext(MatchSnapshot match, UUID playerId, String cause) {
    /** Creates a validated value; null components are rejected. */
    public EliminationContext {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");
    }
}
