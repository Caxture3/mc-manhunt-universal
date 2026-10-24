package com.caxture.randomanhunt.game;

import java.util.Optional;

public record StartResult(StartStatus status, Optional<GameSession> session) {
    public static StartResult started(GameSession session) {
        return new StartResult(StartStatus.STARTED, Optional.of(session));
    }

    public static StartResult rejected(StartStatus status) {
        return new StartResult(status, Optional.empty());
    }
}
