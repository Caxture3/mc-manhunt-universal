package com.caxture.manhuntuniversal.config;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTransitionTest {
    @Test void countdownConfigurationChangesDoNotChangeThisMatchesFrozenSearchSettings() {
        var config=new ConfigurationService(temporary.resolve("config.yml").toFile(),new AtomicFileWriter(),Logger.getAnonymousLogger());
        var registry=install(config);registry.beginMatch(UUID.randomUUID());
        config.update(IntConfigKey.TRACKING_SEARCH_RADIUS_BLOCKS,8192);
        assertEquals(4096,config.matchValues().trackingSettings().searchRadiusBlocks());
        assertEquals(8192,config.values().trackingSettings().searchRadiusBlocks());
    }
    @TempDir Path temporary;
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:config"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return true; } public void close() { }
    };
    private ConfigurationRegistryService install(ConfigurationService configuration) {
        var guard = new OwnedRegistry(() -> {}, () -> null, token -> token == owner, exception -> fail(exception));
        var registry = new ConfigurationRegistryService(guard, ignored -> {});
        new CoreConfigurationBindings(configuration, registry, LocalizedText::of);
        return registry;
    }
    @Test void coreReadersUseEffectiveValuesAndOverlayCloseNotifiesCurrentValue() {
        var config = new ConfigurationService(temporary.resolve("config.yml").toFile(), new AtomicFileWriter(), Logger.getAnonymousLogger());
        var registry = install(config); List<Integer> changed = new ArrayList<>(); config.onRunnerCountChanged(changed::add);
        var overlay = registry.override(owner, "runner-count", ignored -> 4, 10).value().orElseThrow();
        assertEquals(4, config.get(IntConfigKey.RUNNER_COUNT));
        assertTrue(config.update(IntConfigKey.RUNNER_COUNT, 2));
        assertEquals(List.of(4), changed);
        assertEquals(4, config.values().get(IntConfigKey.RUNNER_COUNT));
        overlay.close(); assertEquals(List.of(4, 2), changed);
        assertEquals(2, config.get(IntConfigKey.RUNNER_COUNT));
    }
    @Test void failedGroupSaveHasNoPartialStateOrUnlockNotification() {
        int[] saves={0};var writer = new AtomicFileWriter((a,b) -> { saves[0]++;throw new IOException("fail"); });
        var config = new ConfigurationService(temporary.resolve("config.yml").toFile(), writer, Logger.getAnonymousLogger());
        var registry = install(config); int[] changed = {0}; config.onStructureUnlockTimesChanged(() -> changed[0]++);
        int before=saves[0];assertFalse(registry.update(owner, RunnerStructureUnlockTimes.CONFIG_KEY,
                Map.of("village", 120, "bastion_remnant", 900, "nether_fortress", -1, "trial_chambers", -1), -1).successful());
        assertEquals(before+1,saves[0]);
        assertEquals(0, changed[0]);
        assertEquals(0, config.values().runnerStructureUnlockTimes().village());
        assertEquals(0, config.values().runnerStructureUnlockTimes().bastionRemnant());
    }
}
