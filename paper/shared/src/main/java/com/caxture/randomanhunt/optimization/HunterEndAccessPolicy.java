package com.caxture.randomanhunt.optimization;

import com.caxture.randomanhunt.game.GameSession;
import com.caxture.randomanhunt.game.Role;
import com.caxture.randomanhunt.game.SessionState;
import java.util.UUID;

public final class HunterEndAccessPolicy {
    private HunterEndAccessPolicy() {}

    public static boolean blocked(GameSession session, UUID playerId, boolean enabled) {
        return enabled && session != null && session.state() == SessionState.RUNNING
                && session.roleOf(playerId).filter(role -> role == Role.HUNTER).isPresent()
                && session.targetEndWorldId().isEmpty();
    }
}
