package com.caxture.manhuntuniversal.config;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerStartPositionsTest {
    @Test void defaultsPreserveWorldSpawnAndPositionsSurviveModeChanges(){
        var defaults=PlayerStartPositions.defaults();assertEquals("world-spawn",defaults.hunter().mode());assertEquals("world-spawn",defaults.runner().mode());
        var point=new StartPosition(UUID.randomUUID(),"world",535,15,235);
        var updated=new PlayerStartPositions(new PlayerStartPositions.Team("custom",List.of(point)),defaults.runner());
        assertEquals(updated,PlayerStartPositions.fromMap(updated.toMap()));
        assertEquals(List.of(point),updated.hunter().withMode("player-spawn").custom());
    }
    @Test void malformedCoordinatesAndDuplicateWorldCoordinatesAreRejected(){
        UUID world=UUID.randomUUID();var first=new StartPosition(world,"world",0,15,0);
        assertThrows(IllegalArgumentException.class,()->new StartPosition(world,"world",Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new PlayerStartPositions.Team("custom",List.of(first,new StartPosition(world,"renamed",-0.0,15,0))));
        assertThrows(IllegalArgumentException.class,()->new PlayerStartPositions.Team("invalid",List.of()));
        assertDoesNotThrow(()->new PlayerStartPositions.Team("custom",List.of(first,new StartPosition(UUID.randomUUID(),"other",0,15,0))));
    }
}
