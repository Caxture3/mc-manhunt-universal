package com.caxture.randomanhunt;

import com.caxture.randomanhunt.command.MhuCommand;
import com.caxture.randomanhunt.command.TeamSelectionPresenter;
import com.caxture.randomanhunt.client.PaperTrackingClientPublisher;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.RoleAssigner;
import com.caxture.randomanhunt.game.TeamPreference;
import com.caxture.randomanhunt.i18n.LanguageService;
import com.caxture.randomanhunt.listener.BlazeSpawnerProtectionListener;
import com.caxture.randomanhunt.listener.GameEventListener;
import com.caxture.randomanhunt.player.CountdownBlindnessService;
import com.caxture.randomanhunt.player.PendingPlayerState;
import com.caxture.randomanhunt.player.PlayerResetService;
import com.caxture.randomanhunt.presentation.IdentityDisplayService;
import com.caxture.randomanhunt.presentation.MatchPresentation;
import com.caxture.randomanhunt.presentation.VanillaMatchPresentation;
import com.caxture.randomanhunt.runtime.ActiveGameCoordinator;
import com.caxture.randomanhunt.runtime.GameStartCoordinator;
import com.caxture.randomanhunt.tracking.CompassTrackingService;
import com.caxture.randomanhunt.tracking.FabricAwareTrackingSelectionPresenter;
import com.caxture.randomanhunt.tracking.PaperDialogTrackingSelectionPresenter;
import com.caxture.randomanhunt.tracking.TrackingCompassListener;
import com.caxture.randomanhunt.tracking.TrackingSelectionTargetMapper;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.Random;

public final class RandoManhuntPlugin extends JavaPlugin {
    private ActiveGameCoordinator activeGame;
    private PaperTrackingClientPublisher clientPublisher;
    private IdentityDisplayService identity;

    @Override
    public void onEnable() {
        Clock clock = Clock.systemUTC();
        ConfigurationService configuration = new ConfigurationService(this);
        LanguageService language = new LanguageService(this, configuration::showChatPrefix);
        GameManager gameManager = new GameManager(new RoleAssigner(new Random()));
        TeamSelectionPresenter teamPresenter = new TeamSelectionPresenter(language);
        configuration.onRunnerCountChanged(capacity -> {
            if (gameManager.activeSession().isPresent()) return;
            gameManager.teamSelections().trimRunners(capacity).forEach(playerId -> {
                var player = getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) language.send(player, "team.capacity-cleared");
            });
        });
        PendingPlayerState pendingPlayers = new PendingPlayerState();
        CountdownBlindnessService blindness = new CountdownBlindnessService(getServer(), pendingPlayers);
        PlayerResetService playerReset = new PlayerResetService(getServer());
        identity = new IdentityDisplayService(getServer());
        gameManager.teamSelections().onChoiceChanged(playerId -> {
            TeamPreference preference = gameManager.teamSelections().preferenceOf(playerId);
            if (gameManager.activeSession().isEmpty() || preference == TeamPreference.RANDOM) {
                identity.updateLobbyChoice(playerId, preference);
            }
        });
        MatchPresentation presentation = new VanillaMatchPresentation(getServer(), language);
        clientPublisher = new PaperTrackingClientPublisher(this);
        CompassTrackingService tracking = new CompassTrackingService(
                this, gameManager, configuration, language,
                new FabricAwareTrackingSelectionPresenter(
                        clientPublisher,
                        new PaperDialogTrackingSelectionPresenter(language)),
                pendingPlayers, clock);
        clientPublisher.setSelectionHandler((player, choice) ->
                tracking.applyRunnerSelection(
                        player,
                        choice.sessionId(),
                        choice.authorizationId(),
                        TrackingSelectionTargetMapper.fromProtocol(choice.selection())));
        getServer().getOnlinePlayers().forEach(tracking::cleanupStale);

        activeGame = new ActiveGameCoordinator(
                this, gameManager, configuration, pendingPlayers, blindness, playerReset,
                identity, presentation, tracking, clock);
        GameStartCoordinator gameStart = new GameStartCoordinator(
                getServer(), gameManager, configuration, language, pendingPlayers,
                blindness, playerReset, activeGame, tracking, clock);
        MhuCommand handler = new MhuCommand(
                gameManager, gameStart, activeGame, configuration, language, presentation, teamPresenter, clock);
        PluginCommand command = Objects.requireNonNull(getCommand("mhu"), "mhu command is missing");
        // Let the executor perform the permission check so its denial is localized per client.
        command.setPermission(null);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new GameEventListener(activeGame, gameManager, teamPresenter), this);
        getServer().getPluginManager().registerEvents(new TrackingCompassListener(tracking), this);
        getServer().getPluginManager().registerEvents(clientPublisher, this);
        getServer().getPluginManager().registerEvents(
                new BlazeSpawnerProtectionListener(language, gameManager, configuration), this);
    }

    @Override
    public void onDisable() {
        if (activeGame != null) {
            activeGame.disable();
        }
        if (identity != null) {
            identity.clearLobbyPreviews();
        }
        if (clientPublisher != null) {
            clientPublisher.shutdown();
        }
    }
}
