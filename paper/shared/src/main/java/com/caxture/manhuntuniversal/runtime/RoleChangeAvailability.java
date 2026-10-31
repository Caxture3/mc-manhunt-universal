package com.caxture.manhuntuniversal.runtime;

import com.caxture.manhuntuniversal.player.PendingPlayerState;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Offline role changes update the roster; platform effects are applied by the normal join path. */
public final class RoleChangeAvailability {
    private RoleChangeAvailability() { }
    public static boolean available(Server server, PendingPlayerState pending, UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) return true;
        return !player.isDead() && !pending.needsReset(playerId);
    }
}
