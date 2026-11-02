package com.caxture.manhuntuniversal.config;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.ConfigTiming;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.config.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class NetherCoordinateScaleConfigurationTest {
    @TempDir Path directory;
    static final String NAME="nether-coordinate-scale";
    IntConfigKey key(){return IntConfigKey.find(NAME).orElseThrow();}
    final ExtensionRegistration owner=new ExtensionRegistration(){public String id(){return "test:scale";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}};
    ConfigurationRegistryService install(ConfigurationService service){
        var owned=new OwnedRegistry(()->{},()->null,token->token==owner,error->fail(error));
        var registry=new ConfigurationRegistryService(owned,ignored->{});new CoreConfigurationBindings(service,registry,LocalizedText::of);return registry;
    }
    @Test void defaultsAndStrictIntegerBounds(){
        assertEquals(8,ConfigurationValues.defaults().get(key()));
        for(int value:new int[]{1,8,32,Integer.MAX_VALUE})assertEquals(value,ConfigurationValues.fromRaw(Map.of(NAME,value),ignored->{}).get(key()));
        for(Object value:List.of(0,-1,8.0,1.5,2147483648L,"4",Double.NaN)){
            var warnings=new ArrayList<String>();assertEquals(8,ConfigurationValues.fromRaw(Map.of(NAME,value),warnings::add).get(key()));assertTrue(warnings.contains(NAME));
        }
        for(String value:List.of("0","-1","1.5","2147483648"))assertThrows(IllegalArgumentException.class,()->com.caxture.manhuntuniversal.command.CommandArguments.integer(key(),value));
    }
    @Test void atomicSaveFailureDoesNotChangeLiveValueOrNotify()throws Exception{
        var file=directory.resolve("config.yml").toFile();var service=new ConfigurationService(file,new AtomicFileWriter(),Logger.getAnonymousLogger());
        assertEquals(8,YamlConfiguration.loadConfiguration(file).getInt(NAME));assertTrue(service.update(key(),4));
        var failing=new ConfigurationService(file,new AtomicFileWriter((a,b)->{throw new IOException("expected failure");}),Logger.getAnonymousLogger());
        int[] notices={0};failing.onNetherCoordinateScaleChanged(()->notices[0]++);
        assertFalse(failing.update(key(),2));assertEquals(4,failing.get(key()));assertEquals(0,notices[0]);assertEquals(4,YamlConfiguration.loadConfiguration(file).getInt(NAME));
    }
    @Test void immediateOverlaysLocksAndPriorFrozenSnapshotsRemainCompatible(){
        var service=new ConfigurationService(directory.resolve("config.yml").toFile(),new AtomicFileWriter(),Logger.getAnonymousLogger());var registry=install(service);
        assertEquals(ConfigTiming.IMMEDIATE,registry.find(NAME).orElseThrow().timing());
        UUID session=UUID.randomUUID();registry.beginMatch(session);var previous=registry.frozenCoreSnapshot();assertFalse(previous.containsKey(NAME));
        registry.endMatch(session);registry.restoreMatch(session,previous);
        var notices=new ArrayList<Integer>();service.onNetherCoordinateScaleChanged(()->notices.add(service.get(key())));
        assertTrue(service.update(key(),4));assertEquals(4,service.matchValues().get(key()));
        var overlay=registry.override(owner,NAME,ignored->2,10).value().orElseThrow();assertEquals(2,service.get(key()));
        assertTrue(service.update(key(),6));assertEquals(2,service.get(key()));overlay.close();assertEquals(6,service.get(key()));
        assertEquals(List.of(4,2,6),notices);assertEquals(previous,registry.frozenCoreSnapshot());
        var lock=registry.lock(owner,NAME,10,"mode").value().orElseThrow();assertFalse(service.update(key(),3));lock.close();
    }
}
