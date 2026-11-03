package com.caxture.manhuntuniversal.persistence;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StartupCheckpointTest {
    @TempDir Path directory;
    @Test void startupDestinationsAndAnchorsSurvivePauseWithoutReassignment()throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),world=UUID.randomUUID();var games=new GameManager(new RoleAssigner(new Random(1)));games.teamSelections().choose(a,TeamPreference.RUNNER,1);
        var game=games.start(List.of(new Participant(a,"A"),new Participant(b,"B")),new MatchSettings(1,true,new TrackingSettingsSnapshot(512,30,4096)),Instant.EPOCH).session().orElseThrow();games.activate(game.id(),Instant.EPOCH);
        var destination=new StartDestination(new StartPosition(world,"world",535,15,235),10,20);
        var plan=StartupPlan.assign(game,30,PlayerStartPositions.defaults(),destination,id->destination).anchor(b,destination);
        var saved=new RuntimeCheckpoint(Instant.EPOCH.plusSeconds(10),Optional.of(game.snapshot()),0,Set.of(a),Map.of(b,Optional.empty()),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Optional.of(plan));
        var store=new RuntimeCheckpointStore(directory.resolve("runtime.yml"),new AtomicFileWriter());store.save(saved);var loaded=store.load().orElseThrow();assertEquals(saved,loaded);
        var resumed=loaded.resumedGame(Instant.EPOCH.plusSeconds(3610)).orElseThrow();var restarted=new GameManager(new RoleAssigner(new Random(1)));restarted.restore(resumed);
        assertEquals(20,HunterStartWaitPolicy.remaining(restarted.activeSession().get(),loaded.startup().get().waitSeconds(),Instant.EPOCH.plusSeconds(3610)));
        assertEquals(destination,loaded.startup().get().targets().get(a));assertEquals(destination,loaded.startup().get().anchors().get(b));
    }
    @Test void onlyCompletelyLegacyFrozenStartupKeysGainDefaults(){
        var old=Map.<String,Object>of("runner-count",1);var migrated=StartupCheckpointMigration.frozen(old,false);
        assertEquals(0,migrated.get("hunter-start-wait-seconds"));assertEquals(PlayerStartPositions.defaults().toMap(),migrated.get("player-start-pos"));
        assertFalse(StartupCheckpointMigration.frozen(Map.of("hunter-start-wait-seconds",30),false).containsKey("player-start-pos"));
        assertEquals(old,StartupCheckpointMigration.frozen(old,true));
    }
}
