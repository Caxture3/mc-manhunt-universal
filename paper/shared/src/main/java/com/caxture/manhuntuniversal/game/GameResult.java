package com.caxture.manhuntuniversal.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record GameResult(
        UUID sessionId,
        GameWinner winner,
        GameResultReason reason,
        Instant settledAt,
        List<Participant> participants,
        List<UUID> runners,
        List<UUID> hunters,
        List<RunnerRound> roundHistory,
        Optional<UUID> deadRunner,
        Optional<UUID> killer
) {
    public GameResult {
        participants = List.copyOf(participants);
        runners = List.copyOf(runners);
        hunters = List.copyOf(hunters);
        roundHistory = List.copyOf(roundHistory);
        deadRunner = deadRunner == null ? Optional.empty() : deadRunner;
        killer = killer == null ? Optional.empty() : killer;
    }
}
