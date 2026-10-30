package com.caxture.manhuntuniversal.runtime;

import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.api.OperationStatus;
import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.config.ConfigurationValues;
import com.caxture.manhuntuniversal.config.IntConfigKey;
import com.caxture.manhuntuniversal.config.TrackingSettings;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.MatchSettings;
import com.caxture.manhuntuniversal.game.Participant;
import com.caxture.manhuntuniversal.game.SessionState;
import com.caxture.manhuntuniversal.game.StartResult;
import com.caxture.manhuntuniversal.game.StartStatus;
import com.caxture.manhuntuniversal.game.TrackingSettingsSnapshot;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.caxture.manhuntuniversal.player.AllOrNothingAction;
import com.caxture.manhuntuniversal.player.CountdownBlindnessService;
import com.caxture.manhuntuniversal.player.PendingPlayerState;
import com.caxture.manhuntuniversal.player.PlayerResetService;
import com.caxture.manhuntuniversal.tracking.CompassTrackingService;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameStartCoordinator {
    private final Server server;
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final LanguageService language;
    private final PendingPlayerState pendingPlayers;
    private final CountdownBlindnessService blindness;
    private final PlayerResetService playerReset;
    private final ActiveGameCoordinator activeGame;
    private final CompassTrackingService tracking;
    private final Clock clock;

    public GameStartCoordinator(
            Server server,
            GameManager gameManager,
            ConfigurationService configuration,
            LanguageService language,
            PendingPlayerState pendingPlayers,
            CountdownBlindnessService blindness,
            PlayerResetService playerReset,
            ActiveGameCoordinator activeGame,
            CompassTrackingService tracking,
            Clock clock
    ) {
        this.server = server;
        this.gameManager = gameManager;
        this.configuration = configuration;
        this.language = language;
        this.pendingPlayers = pendingPlayers;
        this.blindness = blindness;
        this.playerReset = playerReset;
        this.activeGame = activeGame;
        this.tracking = tracking;
        this.clock = clock;
    }

    public boolean start(CommandSender sender, int countdownSeconds) {
        return startDetailed(sender, countdownSeconds) == OperationStatus.OK;
    }

    public OperationStatus startForApi(int countdownSeconds) {
        if (countdownSeconds < 0 || countdownSeconds > 60) return OperationStatus.INVALID_ARGUMENT;
        return startDetailed(server.getConsoleSender(), countdownSeconds);
    }

    private OperationStatus startDetailed(CommandSender sender, int countdownSeconds) {
        return activeGame.extensionHooks().inMutation(() -> doStart(sender, countdownSeconds));
    }

    private OperationStatus doStart(CommandSender sender, int countdownSeconds) {
        List<Player> online = new ArrayList<>(server.getOnlinePlayers());
        online.sort(Comparator.comparing(player -> player.getUniqueId().toString()));
        List<Participant> participants = online.stream()
                .map(player -> new Participant(player.getUniqueId(), player.getName()))
                .toList();
        ConfigurationValues values = configuration.values();
        StartResult result = gameManager.start(participants, settings(values), Instant.now(clock));
        if (result.status() == StartStatus.ALREADY_RUNNING) {
            language.send(sender, "game.start.already-running");
            return OperationStatus.START_REJECTED;
        }
        if (result.status() == StartStatus.NOT_ENOUGH_PLAYERS) {
            language.send(sender, "game.start.not-enough-players", Map.of(
                    "count", Integer.toString(values.get(IntConfigKey.RUNNER_COUNT) + 1)));
            return OperationStatus.START_REJECTED;
        }
        if (result.status() == StartStatus.NOT_ENOUGH_RANDOM_PLAYERS) {
            language.send(sender, "game.start.not-enough-random", Map.of(
                    "count", Integer.toString(result.missingRunners())));
            return OperationStatus.START_REJECTED;
        }
        if (result.status() == StartStatus.RUNNER_SELECTION_LIMIT_EXCEEDED) {
            language.send(sender, "team.runner-full", Map.of(
                    "count", Integer.toString(values.get(IntConfigKey.RUNNER_COUNT))));
            return OperationStatus.START_REJECTED;
        }
        GameSession session = result.session().orElseThrow();
        long revision = session.revision();
        if (!activeGame.extensionHooks().preparing(session, countdownSeconds)) {
            activeGame.abortStart(session.id());
            return OperationStatus.CANCELLED;
        }
        if (gameManager.activeSession().orElse(null) != session || session.revision() != revision
                || session.state() != SessionState.COUNTDOWN) return OperationStatus.CANCELLED;
        if (countdownSeconds == 0) {
            return activate(session, sender) ? OperationStatus.OK : OperationStatus.START_REJECTED;
        }
        online.forEach(blindness::apply);
        activeGame.beginCountdown(session, countdownSeconds, () -> activate(session, sender));
        activeGame.extensionHooks().countdown(session, countdownSeconds);
        return gameManager.activeSession().orElse(null) == session ? OperationStatus.OK : OperationStatus.CANCELLED;
    }

    private boolean activate(GameSession session, CommandSender sender) {
        // Countdown completion runs on a later tick, outside the original start() mutation scope.
        return activeGame.extensionHooks().inMutation(() -> activateGuarded(session, sender));
    }

    private boolean activateGuarded(GameSession session, CommandSender sender) {
        GameSession current = gameManager.activeSession().orElse(null);
        if (current == null || !current.id().equals(session.id())
                || current.state() != SessionState.COUNTDOWN) {
            return false;
        }
        if (session.settings().resetPlayersAtStart() && !preparePlayers(session)) {
            language.send(sender, "game.start.teleport-failed");
            activeGame.abortStart(session.id());
            return false;
        }
        if (!session.settings().resetPlayersAtStart()) {
            List<Player> onlineParticipants = session.participants().stream()
                    .map(participant -> server.getPlayer(participant.id()))
                    .filter(player -> player != null && player.isOnline())
                    .toList();
            List<String> noSpace = tracking.playersWithoutSpace(session, onlineParticipants);
            if (!noSpace.isEmpty()) {
                Map<String, String> placeholders = Map.of("players", String.join(", ", noSpace));
                onlineParticipants.forEach(player -> language.send(
                        player, "game.start.compass-no-space", placeholders));
                if (!(sender instanceof Player player) || !onlineParticipants.contains(player)) {
                    language.send(sender, "game.start.compass-no-space", placeholders);
                }
                activeGame.abortStart(session.id());
                return false;
            }
            blindness.restoreAllOnline();
        }
        return activeGame.activate(session);
    }

    private boolean preparePlayers(GameSession session) {
        List<Player> onlineParticipants = session.participants().stream()
                .map(participant -> server.getPlayer(participant.id()))
                .filter(player -> player != null && player.isOnline())
                .toList();
        Map<UUID, Location> originalLocations = new LinkedHashMap<>();
        onlineParticipants.forEach(player -> originalLocations.put(
                player.getUniqueId(), player.getLocation().clone()));
        Location spawn = server.getWorlds().getFirst().getSpawnLocation();
        boolean teleported = AllOrNothingAction.execute(
                onlineParticipants,
                player -> player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN),
                player -> player.teleport(
                        originalLocations.get(player.getUniqueId()), PlayerTeleportEvent.TeleportCause.PLUGIN));
        if (!teleported) {
            return false;
        }
        for (Player player : onlineParticipants) {
            if (gameManager.activeSession().orElse(null) != session || session.state() != SessionState.COUNTDOWN) return false;
            playerReset.reset(player);
            pendingPlayers.completeReset(player.getUniqueId());
            blindness.discard(player.getUniqueId());
        }
        if (gameManager.activeSession().orElse(null) != session || session.state() != SessionState.COUNTDOWN) return false;
        session.participants().forEach(participant -> {
            if (server.getPlayer(participant.id()) == null
                    || !server.getPlayer(participant.id()).isOnline()) {
                pendingPlayers.markResetPending(participant.id());
            }
        });
        return true;
    }

    private static MatchSettings settings(ConfigurationValues values) {
        TrackingSettings tracking = values.trackingSettings();
        return new MatchSettings(
                values.get(IntConfigKey.RUNNER_COUNT),
                values.get(BooleanConfigKey.RESET_PLAYERS_AT_START),
                new TrackingSettingsSnapshot(
                        tracking.retargetDistanceBlocks(),
                        tracking.retargetCooldownSeconds(),
                        tracking.searchRadiusBlocks()));
    }
}
