package com.caxture.manhuntuniversal.player;

import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.SessionState;
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
        if (!session.isEliminatedParticipant(player.getUniqueId())) return;
        OriginalMode original = originals.computeIfAbsent(player.getUniqueId(),
                ignored -> new OriginalMode(session.id(), player.getGameMode()));
        enqueue(player.getUniqueId(), original);
    }

    public void joined(Player player) {
        OriginalMode original = originals.get(player.getUniqueId());
        if (original == null) {
            // Extensions may eliminate participants while they are offline.
            gameManager.activeSession().filter(s -> s.state() == SessionState.RUNNING
                    && s.isEliminatedParticipant(player.getUniqueId())).ifPresent(s -> eliminated(s, player));
            return;
        }
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

    public void revived(UUID playerId) {
        OriginalMode original = originals.get(playerId);
        Player player = server.getPlayer(playerId);
        if (original != null && player != null && player.isOnline()) {
            if (player.isDead()) {
                nextTick.accept(() -> {
                    if (originals.get(playerId) != original || stillEliminated(playerId, original)) return;
                    Player current = server.getPlayer(playerId);
                    if (current == null || !current.isOnline()) return;
                    if (current.isDead()) current.spigot().respawn();
                    if (originals.get(playerId) == original && !stillEliminated(playerId, original)) restore(current, original);
                });
            } else restore(player, original);
        }
        // Offline restoration debt is intentionally retained until joined().
    }

    private void enqueue(UUID playerId, OriginalMode expected) {
        nextTick.accept(() -> {
            if (originals.get(playerId) != expected) return;
            Player player = server.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            if (!stillEliminated(playerId, expected)) {
                // A revival may race the already queued death callback in this same match.
                if (player.isDead() && gameManager.activeSession().filter(session ->
                        session.id().equals(expected.sessionId()) && session.state() == SessionState.RUNNING).isPresent()) {
                    player.spigot().respawn();
                }
                if (originals.get(playerId) == expected && !stillEliminated(playerId, expected)) restore(player, expected);
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
                .filter(session -> session.isEliminatedParticipant(playerId)).isPresent();
    }

    private void restore(Player player, OriginalMode original) {
        if (originals.get(player.getUniqueId()) != original) return;
        if (player.getGameMode() != original.gameMode()) player.setGameMode(original.gameMode());
        if (!stillEliminated(player.getUniqueId(), original)) originals.remove(player.getUniqueId(), original);
    }

    private record OriginalMode(UUID sessionId, GameMode gameMode) {}
}
