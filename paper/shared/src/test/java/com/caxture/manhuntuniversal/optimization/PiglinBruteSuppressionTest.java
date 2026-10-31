package com.caxture.manhuntuniversal.optimization;

import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PiglinBruteSuppressionTest {
    private boolean running=true,enabled=true,core=true;
    private final List<Entity> removed=new ArrayList<>();
    private final Entity brute=entity(EntityType.PIGLIN_BRUTE),piglin=entity(EntityType.PIGLIN);
    private final PiglinBruteSuppressionListener listener=new PiglinBruteSuppressionListener(
            ()->running&&enabled,()->List.of(brute,piglin),entity->core);
    private Entity entity(EntityType type){return (Entity)Proxy.newProxyInstance(Entity.class.getClassLoader(),new Class[]{Entity.class},(proxy,method,args)->switch(method.getName()){
        case "getType"->type;case "remove"->{removed.add((Entity)proxy);yield null;}
        case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);
        default->throw new AssertionError(method.getName());
    });}
    @Test void spawnAndLoadedEntitiesAreSuppressedOnlyWhileEnabledAndRunning(){
        var spawn=new EntitySpawnEvent(brute);listener.spawned(spawn);assertTrue(spawn.isCancelled());
        var normal=new EntitySpawnEvent(piglin);listener.spawned(normal);assertFalse(normal.isCancelled());
        listener.reconcile();assertEquals(List.of(brute),removed);removed.clear();
        Chunk chunk=(Chunk)Proxy.newProxyInstance(Chunk.class.getClassLoader(),new Class[]{Chunk.class},(proxy,method,args)->{if(method.getName().equals("getWorld"))return null;throw new AssertionError(method.getName());});
        listener.loaded(new EntitiesLoadEvent(chunk,List.of(brute,piglin)));assertEquals(List.of(brute),removed);
        for(int mode=0;mode<3;mode++){
            running=mode!=0;enabled=mode!=1;core=mode!=2;removed.clear();
            var event=new EntitySpawnEvent(brute);listener.spawned(event);listener.reconcile();
            assertFalse(event.isCancelled());assertTrue(removed.isEmpty());
        }
    }
    @Test void neverUncancelsAnotherPluginsSpawn(){enabled=false;var event=new EntitySpawnEvent(brute);event.setCancelled(true);listener.spawned(event);assertTrue(event.isCancelled());}
}
