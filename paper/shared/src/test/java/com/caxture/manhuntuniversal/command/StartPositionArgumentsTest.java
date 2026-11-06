package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.config.StartPosition;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StartPositionArgumentsTest {
    final World overworld=world(1,"world",World.Environment.NORMAL);
    final World nether=world(2,"world_nether",World.Environment.NETHER);
    final World end=world(3,"world_the_end",World.Environment.THE_END);
    final World other=world(4,"otherworld",World.Environment.NORMAL);
    final World spaced=world(5,"other world",World.Environment.NORMAL);
    final List<World> worlds=List.of(overworld,nether,end,other,spaced);
    final Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(proxy,method,args)->switch(method.getName()){
        case "getWorlds"->worlds;
        case "getWorld"->worlds.stream().filter(world->args[0].equals(world.getUID())||args[0].equals(world.getName())).findFirst().orElse(null);
        default->throw new UnsupportedOperationException(method.getName());
    });
    @Test void optionalArgumentsDefaultIndependentlyAndExplicitWorldsResolve() {
        var plain=parse("-31","87","-12");assertEquals(overworld.getUID(),plain.worldId());assertEquals(0.0,plain.toMap().get("yaw"));assertEquals(0.0,plain.toMap().get("pitch"));
        var onlyWorld=parse("-31","87","-12","the_nether");assertEquals(nether.getUID(),onlyWorld.worldId());assertEquals(0.0,onlyWorld.toMap().get("yaw"));
        var onlyRotation=parse("-31","87","-12","180","-15.5");assertEquals(overworld.getUID(),onlyRotation.worldId());assertEquals(180.0,onlyRotation.toMap().get("yaw"));
        var full=parse("-31","87","-12","180","0",other.getUID().toString());assertEquals(other.getUID(),full.worldId());assertEquals(180.0,full.toMap().get("yaw"));
        assertEquals(end.getUID(),parse("0","70","0","minecraft:the_end").worldId());
        assertEquals(other.getUID(),parse("0","70","0","otherworld").worldId());
    }
    @Test void invalidArgumentsNeverCreatePositions() {
        assertThrows(IllegalArgumentException.class,()->parse("1","2"));
        assertThrows(IllegalArgumentException.class,()->parse("1","2","3","180"));
        assertThrows(IllegalArgumentException.class,()->parse("1","2","3","180","0","missing"));
        assertThrows(IllegalArgumentException.class,()->parse("NaN","2","3"));
        assertThrows(IllegalArgumentException.class,()->parse("1","2","3","Infinity","0"));
        assertThrows(IllegalArgumentException.class,()->parse("1","2","3","180","0","world","extra"));
    }
    @Test void displayAndSuggestionIncludeWorldCoordinatesAndRotationWithoutConfusingMultipleWorlds() {
        var point=new StartPosition(overworld.getUID(),overworld.getName(),-31,87,-12,180,0);
        assertEquals("(overworld: -31.0, 87.0, -12.0; 180.0°, 0.0°)",StartPositionArguments.describe(server,point));
        assertEquals("/mhu config player-start-pos.hunter.add-custom -31.0 87.0 -12.0 180.0 0.0 otherworld",
                StartPositionArguments.suggestion(server,"hunter",new Location(other,-31,87,-12,180,0)));
        assertEquals("/mhu config player-start-pos.runner.add-custom -31.0 87.0 -12.0 180.0 0.0 the_nether",
                StartPositionArguments.suggestion(server,"runner",new Location(nether,-31,87,-12,180,0)));
        var suggested=StartPositionArguments.suggestion(server,"hunter",new Location(spaced,-31,87,-12,180,0));
        var restored=StartPositionArguments.parse(server,Arrays.copyOfRange(suggested.split(" "),3,9));
        assertEquals(spaced.getUID(),restored.worldId());assertEquals(180.0,restored.toMap().get("yaw"));
    }
    private StartPosition parse(String... args){return StartPositionArguments.parse(server,args);}
    private static World world(long id,String name,World.Environment environment){return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->switch(method.getName()){
        case "getUID"->new UUID(0,id);case "getName"->name;case "getEnvironment"->environment;
        case "equals"->proxy==args[0];case "hashCode"->Long.hashCode(id);default->throw new UnsupportedOperationException(method.getName());
    });}
}
