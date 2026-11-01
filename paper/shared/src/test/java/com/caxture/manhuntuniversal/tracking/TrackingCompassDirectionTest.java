package com.caxture.manhuntuniversal.tracking;
import org.bukkit.Location;
import org.bukkit.inventory.meta.CompassMeta;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TrackingCompassDirectionTest {
    @Test void pausedCompassHasAnEmptyLodestoneTargetRatherThanWorldOriginOrSpawn(){
        List<String> calls=new ArrayList<>();
        CompassMeta meta=(CompassMeta)Proxy.newProxyInstance(CompassMeta.class.getClassLoader(),new Class<?>[]{CompassMeta.class},(proxy,method,args)->{
            calls.add(method.getName());
            if(method.getName().equals("setLodestone")){assertNull(args[0],"No coordinate may be sent, including out-of-height coordinates");return null;}
            if(method.getName().equals("setLodestoneTracked")){assertEquals(false,args[0]);return null;}
            throw new AssertionError("Unexpected compass operation: "+method.getName());
        });
        TrackingCompassDirection.pause(meta);assertEquals(List.of("setLodestone","setLodestoneTracked"),calls);
    }
}
