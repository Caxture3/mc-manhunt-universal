package com.caxture.manhuntuniversal.config;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
class StartSetupConfigurationTest {
    @TempDir Path directory;
    final ExtensionRegistration owner=new ExtensionRegistration(){public String id(){return "test:start";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}};
    @Test void waitAndStructuredPositionsAreFrozenTogetherAndSavedAtomically(){
        var service=new ConfigurationService(directory.resolve("config.yml").toFile(),new AtomicFileWriter(),Logger.getAnonymousLogger());
        var registry=new ConfigurationRegistryService(new OwnedRegistry(()->{},()->null,t->t==owner,e->fail(e)),ignored->{});
        new CoreConfigurationBindings(service,registry,LocalizedText::of);
        assertEquals(0,service.get(IntConfigKey.HUNTER_START_WAIT_SECONDS));
        assertEquals(ConfigTiming.NEXT_MATCH,registry.find("player-start-pos").orElseThrow().timing());
        registry.beginMatch(UUID.randomUUID());assertTrue(service.update(IntConfigKey.HUNTER_START_WAIT_SECONDS,30));
        assertEquals(0,service.matchValues().get(IntConfigKey.HUNTER_START_WAIT_SECONDS));
        assertTrue(registry.update(owner,"player-start-pos.hunter.mode","player-spawn",-1).successful());
        assertEquals("player-spawn",service.values().playerStartPositions().hunter().mode());assertEquals("world-spawn",service.matchValues().playerStartPositions().hunter().mode());
        var reload=new ConfigurationService(directory.resolve("config.yml").toFile(),new AtomicFileWriter(),Logger.getAnonymousLogger());assertEquals("player-spawn",reload.values().playerStartPositions().hunter().mode());
    }
    @Test void failedListSaveKeepsDiskLiveAndFrozenValuesAndRetryDoesNotLoseEntries()throws Exception{
        var file=directory.resolve("config.yml").toFile();new ConfigurationService(file,new AtomicFileWriter(),Logger.getAnonymousLogger());boolean[] fail={true};
        var service=new ConfigurationService(file,new AtomicFileWriter((from,to)->{if(fail[0])throw new java.io.IOException("expected failure");java.nio.file.Files.move(from,to,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}),Logger.getAnonymousLogger());
        var registry=new ConfigurationRegistryService(new OwnedRegistry(()->{},()->null,t->t==owner,e->fail(e)),ignored->{});new CoreConfigurationBindings(service,registry,LocalizedText::of);
        var point=new StartPosition(UUID.randomUUID(),"world",1,70,2);var root="player-start-pos.hunter.custom";
        assertFalse(registry.update(owner,root,List.of(point.toMap()),-1).successful());assertTrue(service.values().playerStartPositions().hunter().custom().isEmpty());
        assertTrue(new ConfigurationService(file,new AtomicFileWriter(),Logger.getAnonymousLogger()).values().playerStartPositions().hunter().custom().isEmpty());
        fail[0]=false;assertTrue(registry.update(owner,root,List.of(point.toMap()),-1).successful());assertTrue(registry.update(owner,"player-start-pos.hunter.mode","custom",-1).successful());
        assertEquals(List.of(point),new ConfigurationService(file,new AtomicFileWriter(),Logger.getAnonymousLogger()).values().playerStartPositions().hunter().custom());
    }
}
