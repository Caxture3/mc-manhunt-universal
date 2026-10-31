package com.caxture.manhuntuniversal.persistence;

import com.caxture.manhuntuniversal.config.AtomicFileWriter;
import com.caxture.manhuntuniversal.extension.config.ConfigurationRegistryService;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.player.PendingPlayerState;
import com.caxture.manhuntuniversal.presentation.IdentityDisplayService;
import com.caxture.manhuntuniversal.runtime.*;
import com.caxture.manhuntuniversal.tracking.CompassTrackingService;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.time.*;
import java.util.*;
import java.util.logging.Level;

/** Main-thread lifecycle bridge; keeps the last valid checkpoint if resume or persistence fails. */
public final class RuntimePersistenceService {
    private final JavaPlugin plugin;
    private final GameManager games;
    private final ActiveGameCoordinator active;
    private final GameStartCoordinator start;
    private final PendingPlayerState pending;
    private final IdentityDisplayService identity;
    private final CompassTrackingService tracking;
    private final ConfigurationRegistryService configs;
    private final Clock clock;
    private final RuntimeCheckpointStore store;
    private BukkitTask periodic,resumeTask;
    private final RuntimeCheckpointGate gate=new RuntimeCheckpointGate();
    private boolean blocked,closing;
    public RuntimePersistenceService(JavaPlugin plugin,GameManager games,ActiveGameCoordinator active,GameStartCoordinator start,
                                     PendingPlayerState pending,IdentityDisplayService identity,CompassTrackingService tracking,
                                     ConfigurationRegistryService configs,Clock clock) {
        this.plugin=plugin;this.games=games;this.active=active;this.start=start;this.pending=pending;this.identity=identity;this.tracking=tracking;this.configs=configs;this.clock=clock;
        store=new RuntimeCheckpointStore(plugin.getDataFolder().toPath().resolve("runtime-state.yml"),new AtomicFileWriter());
    }
    public void initialize() {
        try {
            Optional<RuntimeCheckpoint> loaded=store.load();
            if(loaded.isPresent())install(loaded.get());
        } catch(Exception error) {
            blocked=true;
            plugin.getLogger().log(Level.SEVERE,"Cannot restore runtime-state.yml; the file is preserved and automatic checkpoints are disabled until the file is repaired/removed and the plugin is restarted",error);
        }
        active.onCheckpointNeeded(this::saveCurrent);
        periodic=plugin.getServer().getScheduler().runTaskTimer(plugin,this::saveCurrent,100L,100L);
    }
    private void install(RuntimeCheckpoint saved) {
        Instant now=Instant.now(clock);Optional<SessionSnapshot> game=saved.resumedGame(now);
        // Validate every potentially failing decoder before installing any live state.
        saved.listRestores().values().forEach(json->{if(!json.equals("null"))GsonComponentSerializer.gson().deserialize(json);});
        if(games.activeSession().isPresent())throw new IllegalStateException("A game already exists before checkpoint restore");
        game.ifPresent(value-> {
            var settings=value.settings();var search=settings.trackingSettings();
            Map<String,Object> expected=Map.of("runner-count",settings.runnerCount(),"reset-players-at-start",settings.resetPlayersAtStart(),
                    "tracking-retarget-distance-blocks",search.retargetDistanceBlocks(),"tracking-retarget-cooldown-seconds",search.retargetCooldownSeconds(),"tracking-search-radius-blocks",search.searchRadiusBlocks());
            if(!saved.frozenConfig().equals(expected))throw new IllegalArgumentException("Frozen settings do not match the saved session");
            configs.restoreMatch(value.id(),saved.frozenConfig());
        });
        game.ifPresent(value->{if(!games.restore(value))throw new IllegalStateException("Cannot install saved game");});
        pending.restoreSnapshot(saved.pendingResets(),saved.blindness());active.restoreSpectators(saved.spectatorModes());identity.restoreListSnapshot(saved.listRestores());
        gate.begin(game.map(SessionSnapshot::id));
        resumeTask=plugin.getServer().getScheduler().runTask(plugin,()-> {
            resumeTask=null;
            try {
                GameSession current=games.activeSession().orElse(null);
                if(game.isEmpty()) {
                    if(current==null)plugin.getServer().getOnlinePlayers().forEach(active::playerJoined);
                    return;
                }
                if(game.isPresent()&&(current==null||!current.id().equals(game.get().id())))return;
                if(current.state()==SessionState.COUNTDOWN) {
                    plugin.getServer().getOnlinePlayers().forEach(active::playerJoined);
                    start.resumeCountdown(current,saved.countdownSeconds());
                } else if(current.state()==SessionState.RUNNING)active.resumeRunning(current,saved.positions(),saved.trackingSelections());
                game.ifPresent(value->plugin.getLogger().info("Resumed game "+value.id()+" ("+value.state()+"); offline time was paused"));
            } catch(RuntimeException|LinkageError error) {
                blocked=true;
                plugin.getLogger().log(Level.SEVERE,"Saved game runtime could not resume; preserving the checkpoint",error);
                active.disable();
            } finally { gate.completed(); }
        });
    }
    public RuntimeCheckpoint capture() {
        Optional<GameSession> game=games.activeSession().filter(value->value.state()!=SessionState.SETTLING);
        return new RuntimeCheckpoint(Instant.now(clock),game.map(GameSession::snapshot),game.filter(value->value.state()==SessionState.COUNTDOWN).map(value->Math.max(1,active.countdownRemaining(value.id()))).orElse(0),
                game.isPresent()?pending.resetSnapshot():Set.of(),pending.blindnessSnapshot(),active.spectatorSnapshot(),identity.snapshotListRestores(),
                game.isPresent()?tracking.selectionSnapshot():Map.of(),game.isPresent()?configs.frozenCoreSnapshot():Map.of(),game.isPresent()?tracking.positionSnapshot():Map.of());
    }
    public void saveCurrent() {
        if(blocked||closing||!gate.mayWrite(games.activeSession().map(GameSession::id)))return;
        try { store.save(capture()); }
        catch(Exception error) { plugin.getLogger().log(Level.SEVERE,"Cannot save runtime-state.yml; the last checkpoint is retained",error); }
    }
    public void suspend() {
        if(periodic!=null){periodic.cancel();periodic=null;}
        if(resumeTask!=null){resumeTask.cancel();resumeTask=null;}
        saveCurrent();closing=true;
        active.suspendForRestart();
    }
}
