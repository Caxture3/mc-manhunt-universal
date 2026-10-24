package com.caxture.randomanhunt;

import com.caxture.randomanhunt.command.RmhCommand;
import com.caxture.randomanhunt.client.PaperTrackingClientPublisher;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.RoleAssigner;
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

    @Override
    public void onEnable() {
        Clock clock = Clock.systemUTC();
        ConfigurationService configuration = new ConfigurationService(this);
        LanguageService language = new LanguageService(this, configuration::showChatPrefix);
        GameManager gameManager = new GameManager(new RoleAssigner(new Random()));
        PendingPlayerState pendingPlayers = new PendingPlayerState();
        CountdownBlindnessService blindness = new CountdownBlindnessService(getServer(), pendingPlayers);
        PlayerResetService playerReset = new PlayerResetService(getServer());
        IdentityDisplayService identity = new IdentityDisplayService(getServer());
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
        RmhCommand handler = new RmhCommand(
                gameManager, gameStart, activeGame, configuration, language, presentation, clock);
        PluginCommand command = Objects.requireNonNull(getCommand("rmh"), "rmh command is missing");
        // Let the executor perform the permission check so its denial is localized per client.
        command.setPermission(null);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new GameEventListener(activeGame), this);
        getServer().getPluginManager().registerEvents(new TrackingCompassListener(tracking), this);
        getServer().getPluginManager().registerEvents(clientPublisher, this);
        getServer().getPluginManager().registerEvents(
                new BlazeSpawnerProtectionListener(language), this);
    }

    @Override
    public void onDisable() {
        if (activeGame != null) {
            activeGame.disable();
        }
        if (clientPublisher != null) {
            clientPublisher.shutdown();
        }
    }
}
