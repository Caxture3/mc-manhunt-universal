package com.caxture.manhuntuniversal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class BlazeRodLootingConfigurationTest {
    @TempDir Path temporary;
    @Test void defaultRangeAndInvalidValueFallbackMatchTheConfiguredLootingLevels() {
        var key = IntConfigKey.BLAZE_ROD_LOOTING_LEVEL;
        assertEquals(key, IntConfigKey.find("blaze-rod-looting-level").orElseThrow());
        assertEquals(0, ConfigurationValues.defaults().get(key));
        for (int value = 0; value <= 5; value++) assertTrue(key.valid(value));
        assertFalse(key.valid(-1));
        assertFalse(key.valid(6));
        assertEquals(0, ConfigurationValues.fromRaw(Map.of(key.configKey(), 6), ignored -> {}).get(key));
    }
    @Test void valuePersistsAndReloadsThroughTheExistingConfigurationService() {
        var file = temporary.resolve("config.yml").toFile();
        var config = new ConfigurationService(file, new AtomicFileWriter(), Logger.getAnonymousLogger());
        assertTrue(config.update(IntConfigKey.BLAZE_ROD_LOOTING_LEVEL, 5));
        assertEquals(5, new ConfigurationService(file, new AtomicFileWriter(), Logger.getAnonymousLogger())
                .get(IntConfigKey.BLAZE_ROD_LOOTING_LEVEL));
    }
}
