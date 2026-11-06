package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.config.StartPosition;
import org.bukkit.Location;
import org.bukkit.Server;
import java.util.*;
public record StartDestination(StartPosition point,float yaw,float pitch){
    public StartDestination{Objects.requireNonNull(point);if(!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Invalid rotation");
        if(yaw==0)yaw=0;if(pitch==0)pitch=0;
        point=new StartPosition(point.worldId(),point.worldName(),point.x(),point.y(),point.z(),yaw,pitch);}
    public static StartDestination from(Location location){return new StartDestination(new StartPosition(location.getWorld().getUID(),location.getWorld().getName(),location.getX(),location.getY(),location.getZ()),location.getYaw(),location.getPitch());}
    public Location location(Server server){var world=server.getWorld(point.worldId());if(world==null)throw new IllegalArgumentException("Start world unavailable: "+point.worldName());return new Location(world,point.x(),point.y(),point.z(),yaw,pitch);}
    public Map<String,Object> toMap(){return point.toMap();}
    public static StartDestination fromMap(Map<?,?> map){return new StartDestination(StartPosition.fromMap(map),((Number)map.get("yaw")).floatValue(),((Number)map.get("pitch")).floatValue());}
}
