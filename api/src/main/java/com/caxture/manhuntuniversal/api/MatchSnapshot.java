package com.caxture.manhuntuniversal.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Detached immutable match data. The revision changes when authoritative match state changes.
 * @param id stable identifier
 * @param revision non-negative authoritative state revision
 * @param state current match lifecycle state
 * @param createdAt match creation time
 * @param startedAt formal start time, absent before start
 * @param participants complete participant roster; defensively copied
 * @param targetEndWorldId bound target End world, when present
 */
public record MatchSnapshot(UUID id, long revision, MatchState state, Instant createdAt, Optional<Instant> startedAt, Map<UUID, ParticipantSnapshot> participants, Optional<UUID> targetEndWorldId) {
    /** Creates a validated value; null components are rejected. */
    public MatchSnapshot {
        Objects.requireNonNull(id, "id");
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(startedAt, "startedAt");
        participants = Map.copyOf(participants);
        Objects.requireNonNull(targetEndWorldId, "targetEndWorldId");
    }
}
