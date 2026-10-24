package com.caxture.randomanhunt.player;

import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.GameSession;
import com.caxture.randomanhunt.game.SessionState;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Session-scoped elimination modes; deferred callbacks always validate their original session. */
public final class RunnerSpectatorService {
    private final Server server;
    private final GameManager gameManager;
    private final Consumer<Runnable> nextTick;
    private final Map<UUID, OriginalMode> originals = new HashMap<>();

    public RunnerSpectatorService(Server server, GameManager gameManager, Consumer<Runnable> nextTick) {
        this.server = server;
        this.gameManager = gameManager;
        this.nextTick = nextTick;
    }

    public void eliminated(GameSession session, Player player) {
        if (!session.isEliminatedRunner(player.getUniqueId())) return;
        OriginalMode original = originals.computeIfAbsent(player.getUniqueId(),
                ignored -> new OriginalMode(session.id(), player.getGameMode()));
        enqueue(player.getUniqueId(), original);
    }

    public void joined(Player player) {
        OriginalMode original = originals.get(player.getUniqueId());
        if (original == null) return;
        if (stillEliminated(player.getUniqueId(), original)) enqueue(player.getUniqueId(), original);
        else restore(player, original);
    }

    public void respawned(Player player) {
        OriginalMode original = originals.get(player.getUniqueId());
        if (original != null) enqueue(player.getUniqueId(), original);
    }

    public void finish(UUID sessionId) {
        Map.copyOf(originals).forEach((playerId, original) -> {
            if (!original.sessionId().equals(sessionId)) return;
            Player player = server.getPlayer(playerId);
            if (player != null && player.isOnline()) restore(player, original);
        });
    }

    public void restoreAllOnline() {
        Map.copyOf(originals).forEach((playerId, original) -> {
            Player player = server.getPlayer(playerId);
            if (player != null && player.isOnline()) restore(player, original);
        });
    }

    private void enqueue(UUID playerId, OriginalMode expected) {
        nextTick.accept(() -> {
            if (originals.get(playerId) != expected) return;
            Player player = server.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            if (!stillEliminated(playerId, expected)) {
                restore(player, expected);
                return;
            }
            if (player.isDead()) player.spigot().respawn();
            // A respawn listener may stop the game or begin another session.
            if (originals.get(playerId) == expected && stillEliminated(playerId, expected)
                    && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    private boolean stillEliminated(UUID playerId, OriginalMode original) {
        return gameManager.activeSession()
                .filter(session -> session.id().equals(original.sessionId()))
                .filter(session -> session.state() == SessionState.RUNNING)
                .filter(session -> session.isEliminatedRunner(playerId)).isPresent();
    }

    private void restore(Player player, OriginalMode original) {
        if (originals.get(player.getUniqueId()) != original) return;
        if (player.getGameMode() != original.gameMode()) player.setGameMode(original.gameMode());
        originals.remove(player.getUniqueId(), original);
    }

    private record OriginalMode(UUID sessionId, GameMode gameMode) {}
}
