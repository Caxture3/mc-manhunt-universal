package com.caxture.manhuntuniversal.config;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.config.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class TrialChambersConfigurationTest {
    @TempDir Path directory;
    private static final String KEY="runner-structure-tracker-unlock-time.trial_chambers";
    @Test void existingThreeFieldConfigurationGainsADisabledTrialFieldWithoutChangingExistingValues() throws Exception {
        var file=directory.resolve("config.yml").toFile();var yaml=new YamlConfiguration();
        yaml.set("runner-structure-tracker-unlock-time.village",120);yaml.set("runner-structure-tracker-unlock-time.bastion_remnant",900);yaml.set("runner-structure-tracker-unlock-time.nether_fortress",1200);yaml.save(file);
        var service=new ConfigurationService(file,new AtomicFileWriter(),Logger.getAnonymousLogger());
        var times=service.values().runnerStructureUnlockTimes();assertEquals(120,times.village());assertEquals(900,times.bastionRemnant());assertEquals(1200,times.netherFortress());assertEquals(-1,times.trialChambers());
        assertEquals(-1,YamlConfiguration.loadConfiguration(file).getInt(KEY));assertEquals(-1,IntConfigKey.find(KEY).orElseThrow().defaultValue());
    }
    @Test void trialLeafUsesTheSameAtomicGroupAndCanBeTheOnlyEnabledTarget() {
        var service=new ConfigurationService(directory.resolve("config.yml").toFile(),new AtomicFileWriter(),Logger.getAnonymousLogger());
        ExtensionRegistration owner=new ExtensionRegistration(){public String id(){return "test:trial";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}};
        var guard=new OwnedRegistry(()->{},()->null,token->token==owner,error->fail(error));var registry=new ConfigurationRegistryService(guard,ignored->{});
        new CoreConfigurationBindings(service,registry,LocalizedText::of);
        assertEquals(-1,registry.find(KEY).orElseThrow().effectiveValue());assertTrue(registry.update(owner,KEY,120,-1).successful());
        assertEquals(120,service.values().runnerStructureUnlockTimes().trialChambers());assertEquals(0,service.values().runnerStructureUnlockTimes().village());
        assertFalse(registry.update(owner,KEY,-2,-1).successful());assertEquals(120,registry.find(KEY).orElseThrow().effectiveValue());
        for(var field:List.of("village","bastion_remnant","nether_fortress"))assertTrue(registry.update(owner,RunnerStructureUnlockTimes.CONFIG_KEY+"."+field,-1,-1).successful());
        assertTrue(service.values().runnerStructureUnlockTimes().anyEnabled());
        assertTrue(registry.update(owner,KEY,-1,-1).successful());assertFalse(service.values().runnerStructureUnlockTimes().anyEnabled());
    }
}
