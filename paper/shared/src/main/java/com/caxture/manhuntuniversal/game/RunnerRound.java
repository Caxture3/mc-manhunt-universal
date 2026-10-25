package com.caxture.manhuntuniversal.game;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RunnerRound(
        List<UUID> runners,
        Instant startedAt,
        Instant endedAt,
        TerminationReason reason
) {
    public RunnerRound {
        runners = List.copyOf(runners);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        Objects.requireNonNull(reason, "reason");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Round end precedes start");
        }
    }

    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }
}
