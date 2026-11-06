package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.config.StartPosition;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import java.util.*;

/** Command parsing and presentation for custom start positions. */
final class StartPositionArguments {
    private StartPositionArguments() {}

    static StartPosition parse(Server server, String[] values) {
        if(values.length<3||values.length>6)throw new IllegalArgumentException("Expected x y z [yaw pitch] [world]");
        boolean rotated=values.length>=5;
        String name=values.length==4?values[3]:values.length==6?values[5]:"overworld";
        World world=findWorld(server,name);
        return new StartPosition(world.getUID(),world.getName(),Double.parseDouble(values[0]),Double.parseDouble(values[1]),Double.parseDouble(values[2]),
                rotated?Float.parseFloat(values[3]):0,rotated?Float.parseFloat(values[4]):0);
    }

    static String describe(Server server,StartPosition point) {
        var world=server.getWorld(point.worldId());
        String name=world==null?point.worldName():worldToken(server,world);
        return "("+name+": "+point.x()+", "+point.y()+", "+point.z()+"; "+point.yaw()+"°, "+point.pitch()+"°)";
    }

    static String suggestion(Server server,String role,Location location) {
        return "/mhu config player-start-pos."+role+".add-custom "+location.getX()+" "+location.getY()+" "+location.getZ()+" "+
                location.getYaw()+" "+location.getPitch()+" "+worldToken(server,location.getWorld());
    }

    static List<String> worlds(Server server){
        var names=new LinkedHashSet<String>();
        for(World world:server.getWorlds())names.add(worldToken(server,world));
        return List.copyOf(names);
    }

    private static World findWorld(Server server,String name){
        var environment=aliasEnvironment(name);
        if(environment!=null)return server.getWorlds().stream().filter(world->world.getEnvironment()==environment).findFirst().orElseThrow(()->new UnknownWorld(name));
        World world;
        try{world=server.getWorld(UUID.fromString(name));}catch(IllegalArgumentException ignored){world=server.getWorld(name);}
        if(world==null)throw new UnknownWorld(name);return world;
    }

    private static String worldToken(Server server,World world){
        String alias=switch(world.getEnvironment()){case NORMAL->"overworld";case NETHER->"the_nether";case THE_END->"the_end";default->null;};
        if(alias!=null&&findWorld(server,alias).getUID().equals(world.getUID()))return alias;
        String name=world.getName();
        if(!name.matches("[A-Za-z0-9_./:-]+")||aliasEnvironment(name)!=null)return world.getUID().toString();
        try{UUID.fromString(name);return world.getUID().toString();}catch(IllegalArgumentException ignored){return name;}
    }

    private static World.Environment aliasEnvironment(String name){
        String key=name.toLowerCase(Locale.ROOT);if(key.startsWith("minecraft:"))key=key.substring(10);
        return switch(key){case "overworld"->World.Environment.NORMAL;case "nether","the_nether"->World.Environment.NETHER;case "end","the_end"->World.Environment.THE_END;default->null;};
    }

    static final class UnknownWorld extends IllegalArgumentException {
        final String world;
        UnknownWorld(String world){super("Unknown loaded world: "+world);this.world=world;}
    }
}
