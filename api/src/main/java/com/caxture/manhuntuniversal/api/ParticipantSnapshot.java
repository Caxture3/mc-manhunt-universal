package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Detached participant identity, current role, and elimination state.
 * @param id stable identifier
 * @param name participant display name
 * @param role current team role
 * @param eliminated whether this participant is eliminated
 */
public record ParticipantSnapshot(UUID id, String name, TeamRole role, boolean eliminated) {
    /** Creates a validated value; null components are rejected. */
    public ParticipantSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
    }
}
