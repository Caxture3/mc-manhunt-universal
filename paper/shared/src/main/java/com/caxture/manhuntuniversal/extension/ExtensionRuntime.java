package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.OperationStatus;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.GameWinner;

import java.util.UUID;

/** Narrow runtime boundary used by the service and its contract tests. */
interface ExtensionRuntime {
    OperationStatus start(int seconds);
    boolean stop();
    boolean available(UUID playerId);
    void rolesChanged(GameSession session);
    boolean eliminate(UUID sessionId, UUID playerId);
    boolean revive(UUID sessionId, UUID playerId);
    boolean finish(UUID sessionId, GameWinner winner, String reason);
}
