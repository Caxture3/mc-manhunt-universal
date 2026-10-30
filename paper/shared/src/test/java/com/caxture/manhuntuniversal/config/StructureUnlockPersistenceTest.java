package com.caxture.manhuntuniversal.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class StructureUnlockPersistenceTest {
    @TempDir Path temporary;

    @Test void successfulLeafSavePreservesOneYamlMappingAndNotifiesOnce() throws IOException { verify(true); }
    @Test void failedLeafSaveKeepsExistingValueAndNeverNotifies() throws IOException { verify(false); }

    private void verify(boolean success) throws IOException {
        var file = temporary.resolve("config.yml").toFile();
        var yaml = new YamlConfiguration();
        for (var key : IntConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        for (var key : BooleanConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        yaml.save(file);
        var writer = success ? new AtomicFileWriter()
                : new AtomicFileWriter((a,b) -> { throw new IOException("Test save failure"); });
        var service = new ConfigurationService(file, writer, Logger.getAnonymousLogger());
        int[] changes = {0};
        service.onStructureUnlockTimesChanged(() -> changes[0]++);
        assertEquals(success, service.update(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE, 120));
        assertEquals(success ? 120 : 0, service.values().runnerStructureUnlockTimes().village());
        var saved = YamlConfiguration.loadConfiguration(file);
        var section = saved.getConfigurationSection("runner-structure-tracker-unlock-time");
        assertNotNull(section);
        assertEquals(java.util.Set.of("village", "bastion_remnant", "nether_fortress"), section.getKeys(false));
        assertEquals(success ? 120 : 0, section.getInt("village"));
        assertEquals(success ? 1 : 0, changes[0]);
        assertTrue(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE.valid(-1));
        assertFalse(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE.valid(-2));
    }
}
