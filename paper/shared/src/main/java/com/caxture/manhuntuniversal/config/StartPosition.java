package com.caxture.manhuntuniversal.config;
import java.util.*;
/** Detached absolute coordinates; world display name is informational, UUID is authoritative. */
public record StartPosition(UUID worldId,String worldName,double x,double y,double z) {
    public StartPosition { Objects.requireNonNull(worldId);Objects.requireNonNull(worldName);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000)throw new IllegalArgumentException("Invalid absolute position");
        if(x==0)x=0;if(y==0)y=0;if(z==0)z=0;
    }
    public Map<String,Object> toMap(){return Map.of("world",worldId.toString(),"world-name",worldName,"x",x,"y",y,"z",z);}
    public static StartPosition fromMap(Map<?,?> value){return new StartPosition(UUID.fromString((String)value.get("world")),(String)value.get("world-name"),number(value.get("x")),number(value.get("y")),number(value.get("z")));}
    private static double number(Object value){if(!(value instanceof Number number))throw new IllegalArgumentException("Expected numeric coordinates");return number.doubleValue();}
    public boolean sameCoordinates(StartPosition other){return worldId.equals(other.worldId)&&x==other.x&&y==other.y&&z==other.z;}
}
