package com.caxture.manhuntuniversal.config;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerStartPositionsTest {
    @Test void rotationRoundTripsAndLegacyPositionsGainZeroRotation(){
        var legacy = new StartPosition(UUID.randomUUID(),"world",-31,87,-12).toMap();
        var rotated = new HashMap<String,Object>(legacy);rotated.put("yaw",180.0);rotated.put("pitch",-15.5);
        assertEquals(180.0,StartPosition.fromMap(rotated).toMap().get("yaw"));
        assertEquals(-15.5,StartPosition.fromMap(rotated).toMap().get("pitch"));
        var old = new HashMap<String,Object>(legacy);old.remove("yaw");old.remove("pitch");
        assertEquals(0.0,StartPosition.fromMap(old).toMap().get("yaw"));assertEquals(0.0,StartPosition.fromMap(old).toMap().get("pitch"));
        rotated.put("pitch",Double.NaN);assertThrows(IllegalArgumentException.class,()->StartPosition.fromMap(rotated));
        rotated.put("pitch",1e300);assertThrows(IllegalArgumentException.class,()->StartPosition.fromMap(rotated));
        rotated.remove("pitch");assertThrows(IllegalArgumentException.class,()->StartPosition.fromMap(rotated));
    }
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
        assertThrows(IllegalArgumentException.class,()->new PlayerStartPositions.Team("custom",List.of(first,new StartPosition(world,"world",0,15,0,180,10))));
        assertThrows(IllegalArgumentException.class,()->new PlayerStartPositions.Team("invalid",List.of()));
        assertDoesNotThrow(()->new PlayerStartPositions.Team("custom",List.of(first,new StartPosition(UUID.randomUUID(),"other",0,15,0))));
    }
}
