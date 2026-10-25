package com.caxture.manhuntuniversal.runtime;

import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.config.TrackingSettings;
import com.caxture.manhuntuniversal.game.CountdownSequence;
import com.caxture.manhuntuniversal.game.CountdownStep;
import com.caxture.manhuntuniversal.game.EndDragonPresenceProbe;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.SessionState;
import com.caxture.manhuntuniversal.game.TerminationReason;
import com.caxture.manhuntuniversal.game.TrackingSettingsSnapshot;
import com.caxture.manhuntuniversal.player.CountdownBlindnessService;
import com.caxture.manhuntuniversal.player.PendingPlayerState;
import com.caxture.manhuntuniversal.player.PlayerResetService;
import com.caxture.manhuntuniversal.player.RunnerSpectatorService;
import com.caxture.manhuntuniversal.presentation.IdentityDisplayService;
import com.caxture.manhuntuniversal.presentation.MatchPresentation;
import com.caxture.manhuntuniversal.tracking.CompassTrackingService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ActiveGameCoordinator {
    private final JavaPlugin plugin;
    private final Server server;
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final PendingPlayerState pendingPlayers;
    private final CountdownBlindnessService blindness;
    private final PlayerResetService playerReset;
    private final IdentityDisplayService identity;
    private final MatchPresentation presentation;
    private final CompassTrackingService tracking;
    private final Clock clock;

    private BukkitTask countdownTask;
    private BukkitTask dragonPresenceTask;
    private final PendingResetRetrier pendingResetRetrier;
    private final RunnerSpectatorService spectators;
    private UUID countdownSessionId;
    private int countdownRemaining;

    public ActiveGameCoordinator(
            JavaPlugin plugin,
            GameManager gameManager,
            ConfigurationService configuration,
            PendingPlayerState pendingPlayers,
            CountdownBlindnessService blindness,
            PlayerResetService playerReset,
            IdentityDisplayService identity,
            MatchPresentation presentation,
            CompassTrackingService tracking,
            Clock clock
    ) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.gameManager = gameManager;
        this.configuration = configuration;
        this.pendingPlayers = pendingPlayers;
        this.blindness = blindness;
        this.playerReset = playerReset;
        this.identity = identity;
        this.presentation = presentation;
        this.tracking = tracking;
        this.clock = clock;
        this.spectators = new RunnerSpectatorService(server, gameManager,
                action -> server.getScheduler().runTask(plugin, action));
        this.pendingResetRetrier = new PendingResetRetrier(action -> {
            BukkitTask task = server.getScheduler().runTaskTimer(plugin, action, 1L, 20L);
            return task::cancel;
        });
    }

    public void beginCountdown(GameSession session, int seconds, Runnable activation) {
        cancelCountdownTask();
        CountdownSequence sequence = new CountdownSequence(seconds);
        countdownSessionId = session.id();
        countdownRemaining = seconds;
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                GameSession current = currentSession(session.id()).orElse(null);
                if (current == null || current.state() != SessionState.COUNTDOWN) {
                    cancelCountdownTask();
                    return;
                }
                CountdownStep step = sequence.next();
                if (step instanceof CountdownStep.Number number) {
                    countdownRemaining = number.value();
                    current.participants().forEach(participant -> {
                        Player player = server.getPlayer(participant.id());
                        if (player != null && player.isOnline()) {
                            blindness.apply(player);
                        }
                    });
                    presentation.showCountdown(current, number.value());
                } else if (step == CountdownStep.Start.INSTANCE) {
                    cancelCountdownTask();
                    activation.run();
                } else {
                    cancelCountdownTask();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public boolean activate(GameSession session) {
        TrackingSettings trackingSettings = configuration.values().trackingSettings();
        GameSession activated = gameManager.activate(
                session.id(), Instant.now(clock),
                new TrackingSettingsSnapshot(
                        trackingSettings.retargetDistanceBlocks(),
                        trackingSettings.retargetCooldownSeconds(),
                        trackingSettings.searchRadiusBlocks())).orElse(null);
        if (activated == null) {
            return false;
        }
        identity.begin(activated);
        tracking.start(activated);
        presentation.showStart(activated);
        // With player reset disabled, a runner may already be in the End at activation.
        activated.livingRunnerIds().forEach(playerId -> {
            Player player = server.getPlayer(playerId);
            if (player != null && player.isOnline() && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                playerChangedWorld(player);
            }
        });
        return true;
    }

    public int countdownRemaining(UUID sessionId) {
        return sessionId.equals(countdownSessionId) ? countdownRemaining : 0;
    }

    public boolean stopAdmin() {
        GameSession session = gameManager.activeSession().orElse(null);
        if (session == null) {
            return false;
        }
        return cancel(session.id(), TerminationReason.ADMIN_STOPPED, true);
    }

    public void disable() {
        gameManager.activeSession().ifPresent(session ->
                cancel(session.id(), TerminationReason.PLUGIN_DISABLED, true));
        cancelAllTasks();
        blindness.restoreAllOnline();
        tracking.shutdown();
        spectators.restoreAllOnline();
        gameManager.teamSelections().clear();
    }

    public void abortStart(UUID expectedSessionId) {
        cancel(expectedSessionId, TerminationReason.ADMIN_STOPPED, false);
    }

    public void playerDied(Player player) {
        GameSession session = runningSession().orElse(null);
        if (session == null || !session.isRunnerAlive(player.getUniqueId())) {
            return;
        }
        Optional<UUID> killer = Optional.ofNullable(player.getKiller()).map(Player::getUniqueId);
        Optional<GameResult> result = gameManager.recordRunnerDeath(
                session.id(), player.getUniqueId(), killer, Instant.now(clock));
        pendingResetRetrier.cancel(player.getUniqueId());
        pendingPlayers.completeReset(player.getUniqueId());
        spectators.eliminated(session, player);
        tracking.runnerEliminated(player);
        result.ifPresent(this::finish);
    }

    public void playerRespawned(Player player) {
        spectators.respawned(player);
    }

    public void playerChangedWorld(Player player) {
        tracking.playerChangedWorld(player, null);
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        GameSession session = runningSession().orElse(null);
        if (session != null && gameManager.bindTargetEnd(
                session.id(), player.getUniqueId(), player.getWorld().getUID())) {
            startDragonPresenceProbe(session.id(), player.getWorld().getUID());
        }
    }

    public void enderDragonDied(UUID worldId) {
        GameSession session = runningSession().orElse(null);
        if (session != null) {
            gameManager.recordEnderDragonDeath(
                    session.id(), worldId, Instant.now(clock)).ifPresent(this::finish);
        }
    }

    public void playerJoined(Player player) {
        identity.restorePending(player);
        spectators.joined(player);
        tracking.cleanupStale(player);
        GameSession session = gameManager.activeSession().orElse(null);
        if (session == null) {
            blindness.restore(player);
            return;
        }
        boolean participant = session.participant(player.getUniqueId()).isPresent();
        if (!participant) {
            blindness.restore(player);
            return;
        }
        if (session.state() == SessionState.COUNTDOWN) {
            blindness.apply(player);
            return;
        }
        if (session.state() != SessionState.RUNNING) {
            blindness.restore(player);
            return;
        }
        if (session.isEliminatedRunner(player.getUniqueId())) {
            blindness.restore(player);
            identity.applyJoiningPlayer(session, player);
            return;
        }
        if (pendingPlayers.needsReset(player.getUniqueId())) {
            if (!tryCompletePendingReset(session, player)) {
                schedulePendingResetRetry(session.id(), player.getUniqueId());
            }
            return;
        }
        blindness.restore(player);
        identity.applyJoiningPlayer(session, player);
        tracking.playerJoined(player, session);
        if (session.isRunnerAlive(player.getUniqueId()) && player.getWorld().getEnvironment() == World.Environment.THE_END) {
            playerChangedWorld(player);
        }
    }

    public void playerQuit(Player player) {
        gameManager.teamSelections().remove(player.getUniqueId());
        pendingResetRetrier.cancel(player.getUniqueId());
        tracking.playerQuit(player);
        // UUID-backed reset and restoration debt intentionally survives the disconnect.
    }

    public void playerTeleported(Player player, org.bukkit.Location from, org.bukkit.Location to) {
        tracking.playerTeleported(player, from, to);
    }

    public void configurationChanged(BooleanConfigKey key) {
        if (key == BooleanConfigKey.FORCE_RANDOM_ROLES) {
            gameManager.teamSelections().setForcedRandom(configuration.get(key));
        } else if (key == BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS) {
            boolean hidden = configuration.get(key);
            identity.setHidden(hidden);
            if (!hidden && gameManager.activeSession().filter(s -> s.state() != SessionState.COUNTDOWN).isEmpty()) {
                gameManager.teamSelections().snapshot().forEach(identity::updateLobbyChoice);
            }
        }
        tracking.configurationChanged(key);
    }

    private void startDragonPresenceProbe(UUID sessionId, UUID worldId) {
        cancelDragonPresenceTask();
        EndDragonPresenceProbe probe = new EndDragonPresenceProbe();
        dragonPresenceTask = new BukkitRunnable() {
            @Override
            public void run() {
                GameSession session = currentSession(sessionId).orElse(null);
                if (session == null || session.state() != SessionState.RUNNING
                        || session.targetEndWorldId().filter(worldId::equals).isEmpty()) {
                    cancelDragonPresenceTask();
                    return;
                }
                World world = server.getWorld(worldId);
                boolean livingDragon = world != null && world.getEntitiesByClass(EnderDragon.class).stream()
                        .anyMatch(dragon -> dragon.isValid() && !dragon.isDead());
                DragonBattle battle = world == null ? null : world.getEnderDragonBattle();
                boolean respawnInProgress = battle != null
                        && battle.getRespawnPhase() != DragonBattle.RespawnPhase.NONE;
                EndDragonPresenceProbe.Outcome outcome = probe.observe(livingDragon, respawnInProgress);
                if (outcome == EndDragonPresenceProbe.Outcome.WAITING) {
                    return;
                }
                cancelDragonPresenceTask();
                if (outcome == EndDragonPresenceProbe.Outcome.ABSENT) {
                    gameManager.recordEnderDragonAbsent(
                            sessionId, worldId, Instant.now(clock)).ifPresent(ActiveGameCoordinator.this::finish);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean tryCompletePendingReset(GameSession session, Player player) {
        if (!player.teleport(
                server.getWorlds().getFirst().getSpawnLocation(),
                PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return false;
        }
        playerReset.reset(player);
        pendingPlayers.completeReset(player.getUniqueId());
        blindness.discard(player.getUniqueId());
        identity.applyJoiningPlayer(session, player);
        tracking.playerJoined(player, session);
        return true;
    }

    private void schedulePendingResetRetry(UUID sessionId, UUID playerId) {
        pendingResetRetrier.start(playerId, () -> {
            GameSession session = currentSession(sessionId)
                    .filter(current -> current.state() == SessionState.RUNNING)
                    .filter(current -> current.participant(playerId).isPresent())
                    .orElse(null);
            Player player = server.getPlayer(playerId);
            if (session == null || player == null || !player.isOnline()
                    || !pendingPlayers.needsReset(playerId)) {
                return PendingResetRetrier.Outcome.CANCEL;
            }
            return tryCompletePendingReset(session, player)
                    ? PendingResetRetrier.Outcome.COMPLETE : PendingResetRetrier.Outcome.RETRY;
        });
    }

    private boolean cancel(UUID expectedSessionId, TerminationReason reason, boolean announce) {
        GameSession session = gameManager.cancel(
                expectedSessionId, reason, Instant.now(clock)).orElse(null);
        if (session == null) {
            return false;
        }
        try {
            if (announce) {
                presentation.showStopped(reason);
            }
        } finally {
            cleanup(session);
        }
        return true;
    }

    private void finish(GameResult result) {
        GameSession session = currentSession(result.sessionId()).orElse(null);
        if (session == null) {
            return;
        }
        try {
            presentation.showResult(result);
        } finally {
            cleanup(session);
        }
    }

    private void cleanup(GameSession session) {
        cancelAllTasks();
        blindness.restoreAllOnline();
        try {
            tracking.finish(session);
        } finally {
            try {
                identity.clear(session);
            } finally {
                try {
                    spectators.finish(session.id());
                } finally {
                    session.participants().forEach(
                            participant -> pendingPlayers.completeReset(participant.id()));
                    gameManager.clearSettled(session.id());
                }
            }
        }
    }

    private Optional<GameSession> currentSession(UUID expectedSessionId) {
        return gameManager.activeSession().filter(session -> session.id().equals(expectedSessionId));
    }

    private Optional<GameSession> runningSession() {
        return gameManager.activeSession().filter(session -> session.state() == SessionState.RUNNING);
    }

    private void cancelAllTasks() {
        cancelCountdownTask();
        cancelDragonPresenceTask();
        pendingResetRetrier.cancelAll();
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        countdownSessionId = null;
        countdownRemaining = 0;
    }

    private void cancelDragonPresenceTask() {
        if (dragonPresenceTask != null) {
            dragonPresenceTask.cancel();
            dragonPresenceTask = null;
        }
    }

}
