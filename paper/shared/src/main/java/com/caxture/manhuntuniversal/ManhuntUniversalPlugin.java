package com.caxture.manhuntuniversal;

import com.caxture.manhuntuniversal.command.MhuCommand;
import com.caxture.manhuntuniversal.command.TeamSelectionPresenter;
import com.caxture.manhuntuniversal.client.PaperTrackingClientPublisher;
import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.RoleAssigner;
import com.caxture.manhuntuniversal.game.TeamPreference;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.caxture.manhuntuniversal.listener.BlazeSpawnerProtectionListener;
import com.caxture.manhuntuniversal.listener.GameEventListener;
import com.caxture.manhuntuniversal.optimization.HunterEndAccessListener;
import com.caxture.manhuntuniversal.optimization.OreFortuneListener;
import com.caxture.manhuntuniversal.optimization.PiglinBarterListener;
import com.caxture.manhuntuniversal.player.CountdownBlindnessService;
import com.caxture.manhuntuniversal.player.PendingPlayerState;
import com.caxture.manhuntuniversal.player.PlayerResetService;
import com.caxture.manhuntuniversal.presentation.IdentityDisplayService;
import com.caxture.manhuntuniversal.presentation.MatchPresentation;
import com.caxture.manhuntuniversal.presentation.VanillaMatchPresentation;
import com.caxture.manhuntuniversal.runtime.ActiveGameCoordinator;
import com.caxture.manhuntuniversal.runtime.GameStartCoordinator;
import com.caxture.manhuntuniversal.tracking.CompassTrackingService;
import com.caxture.manhuntuniversal.tracking.FabricAwareTrackingSelectionPresenter;
import com.caxture.manhuntuniversal.tracking.PaperDialogTrackingSelectionPresenter;
import com.caxture.manhuntuniversal.tracking.TrackingCompassListener;
import com.caxture.manhuntuniversal.tracking.TrackingSelectionTargetMapper;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.Random;

public final class ManhuntUniversalPlugin extends JavaPlugin {
    private ActiveGameCoordinator activeGame;
    private PaperTrackingClientPublisher clientPublisher;
    private IdentityDisplayService identity;

    @Override
    public void onEnable() {
        Clock clock = Clock.systemUTC();
        ConfigurationService configuration = new ConfigurationService(this);
        LanguageService language = new LanguageService(this, configuration::showChatPrefix);
        GameManager gameManager = new GameManager(new RoleAssigner(new Random()));
        gameManager.teamSelections().setForcedRandom(configuration.get(BooleanConfigKey.FORCE_RANDOM_ROLES));
        TeamSelectionPresenter teamPresenter = new TeamSelectionPresenter(language,
                () -> !gameManager.teamSelections().forcedRandom());
        configuration.onRunnerCountChanged(capacity -> {
            if (gameManager.activeSession().isPresent()) return;
            gameManager.teamSelections().trimRunners(capacity).forEach(playerId -> {
                var player = getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) language.send(player,
                        configuration.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS)
                                ? "team.capacity-cleared-hidden" : "team.capacity-cleared");
            });
        });
        PendingPlayerState pendingPlayers = new PendingPlayerState();
        CountdownBlindnessService blindness = new CountdownBlindnessService(getServer(), pendingPlayers);
        PlayerResetService playerReset = new PlayerResetService(getServer());
        identity = new IdentityDisplayService(getServer());
        identity.setHidden(configuration.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS));
        gameManager.teamSelections().onChoiceChanged(playerId -> {
            TeamPreference preference = gameManager.teamSelections().preferenceOf(playerId);
            if (gameManager.activeSession().isEmpty() || preference == TeamPreference.RANDOM) {
                identity.updateLobbyChoice(playerId, preference);
            }
        });
        MatchPresentation presentation = new VanillaMatchPresentation(getServer(), language,
                () -> configuration.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS));
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
        configuration.onBooleanChanged(activeGame::configurationChanged);
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
        getServer().getPluginManager().registerEvents(new OreFortuneListener(this, gameManager, configuration), this);
        getServer().getPluginManager().registerEvents(new PiglinBarterListener(gameManager, configuration), this);
        getServer().getPluginManager().registerEvents(new HunterEndAccessListener(gameManager, configuration, language), this);
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
