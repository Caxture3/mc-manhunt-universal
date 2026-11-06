package com.caxture.manhuntuniversal.config;
import java.util.*;
/** Detached absolute coordinates; world display name is informational, UUID is authoritative. */
public record StartPosition(UUID worldId,String worldName,double x,double y,double z,float yaw,float pitch) {
    public StartPosition(UUID worldId,String worldName,double x,double y,double z){this(worldId,worldName,x,y,z,0,0);}
    public StartPosition { Objects.requireNonNull(worldId);Objects.requireNonNull(worldName);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000)throw new IllegalArgumentException("Invalid absolute position");
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Invalid rotation");
        if(x==0)x=0;if(y==0)y=0;if(z==0)z=0;
        if(yaw==0)yaw=0;if(pitch==0)pitch=0;
    }
    public Map<String,Object> toMap(){return Map.of("world",worldId.toString(),"world-name",worldName,"x",x,"y",y,"z",z,"yaw",(double)yaw,"pitch",(double)pitch);}
    public static StartPosition fromMap(Map<?,?> value){
        if(value.containsKey("yaw")!=value.containsKey("pitch"))throw new IllegalArgumentException("Incomplete rotation");
        return new StartPosition(UUID.fromString((String)value.get("world")),(String)value.get("world-name"),number(value.get("x")),number(value.get("y")),number(value.get("z")),
                value.containsKey("yaw")?(float)number(value.get("yaw")):0,value.containsKey("pitch")?(float)number(value.get("pitch")):0);}
    private static double number(Object value){if(!(value instanceof Number number))throw new IllegalArgumentException("Expected numeric coordinates");return number.doubleValue();}
    public boolean sameCoordinates(StartPosition other){return worldId.equals(other.worldId)&&x==other.x&&y==other.y&&z==other.z;}
}
