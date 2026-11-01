package com.caxture.manhuntuniversal.tracking;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerTrackingIndexTest {
    final UUID world=UUID.randomUUID(), outsider=UUID.randomUUID(), runner=UUID.randomUUID();
    MatchSnapshot match(boolean eliminated){return new MatchSnapshot(UUID.randomUUID(),1,MatchState.RUNNING,Instant.EPOCH,Optional.of(Instant.EPOCH),
        Map.of(runner,new ParticipantSnapshot(runner,"Runner",TeamRole.RUNNER,eliminated)),Optional.empty());}
    @Test void outsidersAndOfflinePositionsAreIncludedButEliminatedParticipantsAreExcluded(){
        var index=new PlayerTrackingIndex(4);var position=new WorldPosition(world,10,60,20);
        index.observe(outsider,"Visitor",position);index.observe(runner,"Runner",position);
        var candidates=index.candidates(match(false),Set.of(runner));assertEquals(2,candidates.size());
        var visitor=candidates.stream().filter(c->c.playerId().equals(outsider)).findFirst().orElseThrow();
        assertFalse(visitor.online());assertTrue(visitor.role().isEmpty());assertEquals(position,visitor.position());
        assertEquals(List.of(outsider),index.candidates(match(true),Set.of()).stream().map(PlayerTrackingCandidate::playerId).toList());
    }
    @Test void observationMemoryIsBoundedAndSnapshotsAreDetached(){
        var index=new PlayerTrackingIndex(1);index.observe(outsider,"Visitor",new WorldPosition(world,0,0,0));
        var old=index.candidates(match(false),Set.of());index.observe(runner,"Runner",new WorldPosition(world,1,0,0));
        assertEquals(outsider,old.getFirst().playerId());assertEquals(runner,index.candidates(match(false),Set.of()).getFirst().playerId());
        index.clear();assertTrue(index.candidates(match(false),Set.of()).isEmpty());
    }
}
