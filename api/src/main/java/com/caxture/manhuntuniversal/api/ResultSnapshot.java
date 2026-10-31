package com.caxture.manhuntuniversal.api;
import java.time.Instant;
import java.util.*;
/** Immutable final result. Redaction removes role-bearing lists and killer, never the actual result. */
public record ResultSnapshot(UUID matchId, MatchWinner winner, String reason, Optional<Instant> startedAt, Instant endedAt,
                             Map<UUID, ParticipantSnapshot> participants, Optional<UUID> killer) {
    public ResultSnapshot {
        Objects.requireNonNull(matchId); Objects.requireNonNull(winner); Objects.requireNonNull(reason);
        Objects.requireNonNull(startedAt); Objects.requireNonNull(endedAt); Objects.requireNonNull(killer);
        participants = Map.copyOf(participants);
    }
    public ResultSnapshot redacted() { return new ResultSnapshot(matchId, winner, reason, startedAt, endedAt, Map.of(), Optional.empty()); }
}
