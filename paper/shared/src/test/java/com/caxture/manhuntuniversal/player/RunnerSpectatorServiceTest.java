package com.caxture.manhuntuniversal.player;

import com.caxture.manhuntuniversal.game.*;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RunnerSpectatorServiceTest {
    private final GameManager manager = new GameManager(new RoleAssigner(new Random(1)));
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final UUID first = new UUID(0, 1), second = new UUID(0, 2);
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Server server = proxy(Server.class, (object, method, args) -> {
        if (method.getName().equals("getPlayer")) {
            PlayerState state = players.get(args[0]);
            return state != null && state.online ? state.player : null;
        }
        throw new UnsupportedOperationException(method.getName());
    });
    private final RunnerSpectatorService spectators = new RunnerSpectatorService(server, manager, tasks::addLast);

    @Test
    void eliminatedRunnerAutomaticallyRespawnsAndSpectatesOnNextTick() {
        GameSession session = start();
        PlayerState player = player(first, GameMode.SURVIVAL);
        eliminate(session, player);
        assertEquals(GameMode.SURVIVAL, player.mode);
        drain();
        assertFalse(player.dead);
        assertEquals(1, player.respawns);
        assertEquals(GameMode.SPECTATOR, player.mode);
    }

    @Test
    void stopRestoresOriginalModeAndQueuedRespawnCannotApplySpectatorAfterwards() {
        GameSession session = start();
        PlayerState player = player(first, GameMode.ADVENTURE);
        eliminate(session, player);
        drain();
        spectators.respawned(player.player);
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, Instant.EPOCH);
        spectators.finish(session.id());
        manager.clearSettled(session.id());
        drain();
        assertEquals(GameMode.ADVENTURE, player.mode);
        assertTrue(manager.teamSelections().snapshot().isEmpty());
    }

    @Test
    void disconnectDoesNotReinstateRunnerAndOfflineFinishRestoresOnNextJoin() {
        GameSession session = start();
        PlayerState player = player(first, GameMode.CREATIVE);
        eliminate(session, player);
        player.online = false;
        drain();
        player.online = true;
        spectators.joined(player.player);
        drain();
        assertEquals(GameMode.SPECTATOR, player.mode);
        assertTrue(session.isEliminatedRunner(first));
        player.online = false;
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, Instant.EPOCH);
        spectators.finish(session.id());
        manager.clearSettled(session.id());
        player.online = true;
        spectators.joined(player.player);
        assertEquals(GameMode.CREATIVE, player.mode);
    }

    @Test
    void lastRunnerDeathSettlesAndOldDeathTaskCannotChangeNewMatchMode() {
        GameSession session = start();
        PlayerState player = player(first, GameMode.SURVIVAL);
        eliminate(session, player);
        assertTrue(manager.recordRunnerDeath(session.id(), second, Optional.empty(), Instant.EPOCH).isPresent());
        spectators.finish(session.id());
        manager.clearSettled(session.id());
        start();
        drain();
        assertEquals(GameMode.SURVIVAL, player.mode);
        assertEquals(0, player.respawns);
    }

    private void eliminate(GameSession session, PlayerState player) {
        manager.recordRunnerDeath(session.id(), player.id, Optional.empty(), Instant.EPOCH);
        spectators.eliminated(session, player.player);
    }

    private GameSession start() {
        manager.teamSelections().choose(first, TeamPreference.RUNNER, 2);
        manager.teamSelections().choose(second, TeamPreference.RUNNER, 2);
        GameSession session = manager.start(List.of(new Participant(first, "First"),
                        new Participant(second, "Second"), new Participant(new UUID(0, 3), "Hunter")),
                new MatchSettings(2, false, new TrackingSettingsSnapshot(512, 30, 4096)), Instant.EPOCH)
                .session().orElseThrow();
        manager.activate(session.id(), Instant.EPOCH);
        return session;
    }

    private PlayerState player(UUID id, GameMode mode) {
        PlayerState player = new PlayerState(id, mode);
        players.put(id, player);
        return player;
    }

    private void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }

    private static final class PlayerState {
        final UUID id;
        GameMode mode;
        boolean online = true, dead = true;
        int respawns;
        final Player player;
        PlayerState(UUID id, GameMode initial) {
            this.id = id;
            mode = initial;
            Player.Spigot spigot = new Player.Spigot() {
                @Override public void respawn() { dead = false; respawns++; }
            };
            player = proxy(Player.class, (object, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getGameMode" -> mode;
                case "setGameMode" -> { mode = (GameMode) args[0]; yield null; }
                case "isOnline" -> online;
                case "isDead" -> dead;
                case "spigot" -> spigot;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
