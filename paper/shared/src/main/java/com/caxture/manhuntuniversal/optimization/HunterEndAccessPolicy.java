package com.caxture.manhuntuniversal.optimization;

import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.Role;
import com.caxture.manhuntuniversal.game.SessionState;
import java.util.UUID;

public final class HunterEndAccessPolicy {
    private HunterEndAccessPolicy() {}

    public static boolean blocked(GameSession session, UUID playerId, boolean enabled) {
        return enabled && session != null && session.state() == SessionState.RUNNING
                && session.roleOf(playerId).filter(role -> role == Role.HUNTER).isPresent()
                && session.targetEndWorldId().isEmpty();
    }
}
