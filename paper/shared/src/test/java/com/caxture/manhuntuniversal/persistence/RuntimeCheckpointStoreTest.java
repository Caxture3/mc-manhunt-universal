package com.caxture.manhuntuniversal.persistence;

import com.caxture.manhuntuniversal.config.AtomicFileWriter;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.player.*;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeCheckpointStoreTest {
    @TempDir Path directory;
    private final Instant at=Instant.parse("2026-11-01T00:00:00Z");
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private RuntimeCheckpoint value(boolean countdown) {
        var manager=new GameManager(new RoleAssigner(new Random(1)));
        var game=manager.start(List.of(new Participant(a,"A"),new Participant(b,"B")),new MatchSettings(1,false,new TrackingSettingsSnapshot(512,30,4096)),at).session().orElseThrow();
        if(!countdown)manager.activate(game.id(),at);
        return new RuntimeCheckpoint(at.plusSeconds(12),Optional.of(game.snapshot()),countdown?7:0,Set.of(a),Map.of(a,Optional.empty(),b,Optional.of(new BlindnessSnapshot(123,1,true,false,false))),
                Map.of(a,new RunnerSpectatorService.OriginalMode(game.id(),GameMode.CREATIVE)),Map.of(b,"{\"text\":\"original\"}"),Map.of(a,"core:village"),Map.of("runner-count",1,"reset-players-at-start",false),Map.of(a,List.of(new com.caxture.manhuntuniversal.tracking.TrackingPosition(UUID.randomUUID(),com.caxture.manhuntuniversal.tracking.TrackingWorldKind.NETHER,1.5,70,2.5))));
    }
    @Test void roundTripsRunningCountdownAndCleanupDebtThenWritesATombstone() throws Exception {
        var store=new RuntimeCheckpointStore(directory.resolve("runtime-state.yml"),new AtomicFileWriter());
        assertTrue(store.load().isEmpty());
        for(boolean countdown:new boolean[]{true,false}){
            var saved=value(countdown);store.save(saved);assertEquals(saved,store.load().orElseThrow());
            var resumed=store.load().orElseThrow().resumedGame(at.plusSeconds(3612)).orElseThrow();
            assertEquals(at.plusSeconds(3600),resumed.createdAt());assertEquals(saved.game().get().revision(),resumed.revision());
        }
        var idle=new RuntimeCheckpoint(at,Optional.empty(),0,Set.of(),Map.of(b,Optional.empty()),Map.of(),Map.of(),Map.of(),Map.of(),Map.of());
        store.save(idle);assertTrue(store.load().orElseThrow().game().isEmpty());assertEquals(idle,store.load().orElseThrow());
    }
    @Test void malformedAndUnsupportedFilesAreRejectedAndLeftUntouched() throws Exception {
        Path file=directory.resolve("runtime-state.yml");var store=new RuntimeCheckpointStore(file,new AtomicFileWriter());
        for(String malformed:List.of("format: 99\n", "format: [broken\n", "format: 1\nsaved-at: yesterday\n")){
            Files.writeString(file,malformed);assertThrows(java.io.IOException.class,store::load);assertEquals(malformed,Files.readString(file));
        }
    }
    @Test void failedWriteCannotReplaceTheLastCheckpoint() throws Exception {
        Path file=directory.resolve("runtime-state.yml");var store=new RuntimeCheckpointStore(file,new AtomicFileWriter());store.save(value(false));byte[] before=Files.readAllBytes(file);
        var failing=new RuntimeCheckpointStore(file,(target,content)->{throw new java.io.IOException("disk full");});
        assertThrows(java.io.IOException.class,()->failing.save(value(true)));assertArrayEquals(before,Files.readAllBytes(file));
    }
}
