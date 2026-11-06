package com.caxture.manhuntuniversal.persistence;
import com.caxture.manhuntuniversal.config.*;
import java.util.*;
/** Only the exact pre-startup schema may gain default frozen keys; partial new snapshots stay invalid. */
public final class StartupCheckpointMigration {
    private StartupCheckpointMigration(){}
    public static Map<String,Object> frozen(Map<String,Object> saved,boolean hasStartup){
        String wait=IntConfigKey.HUNTER_START_WAIT_SECONDS.configKey();Map<String,Object> result=new LinkedHashMap<>(saved);
        if(!hasStartup&&!saved.containsKey(wait)&&!saved.containsKey(PlayerStartPositions.KEY)){
            result.put(wait,0);result.put(PlayerStartPositions.KEY,PlayerStartPositions.defaults().toMap());
        }
        result.computeIfPresent(PlayerStartPositions.KEY,(key,value)->PlayerStartPositions.type().validate(value));
        return Map.copyOf(result);
    }
}
