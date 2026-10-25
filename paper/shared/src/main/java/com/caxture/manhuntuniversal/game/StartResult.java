package com.caxture.manhuntuniversal.game;

import java.util.Optional;

public record StartResult(StartStatus status, Optional<GameSession> session, int missingRunners) {
    public static StartResult started(GameSession session) {
        return new StartResult(StartStatus.STARTED, Optional.of(session), 0);
    }

    public static StartResult rejected(StartStatus status) {
        return new StartResult(status, Optional.empty(), 0);
    }

    public static StartResult missingRandomPlayers(int count) {
        return new StartResult(StartStatus.NOT_ENOUGH_RANDOM_PLAYERS, Optional.empty(), count);
    }
}
