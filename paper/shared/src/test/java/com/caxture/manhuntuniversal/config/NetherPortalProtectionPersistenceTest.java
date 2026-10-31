package com.caxture.manhuntuniversal.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class NetherPortalProtectionPersistenceTest {
    @TempDir Path temporary;

    @Test void defaultsMissingAndInvalidValuesRemainDisabled() {
        var key = BooleanConfigKey.PREVENT_NETHER_PORTAL_DESTRUCTION;
        assertEquals(key, BooleanConfigKey.find("prevent-nether-portal-destruction").orElseThrow());
        assertFalse(ConfigurationValues.defaults().get(key));
        assertFalse(ConfigurationValues.fromRaw(Map.of(), ignored -> {}).get(key));
        assertFalse(ConfigurationValues.fromRaw(Map.of(key.configKey(), "true"), ignored -> {}).get(key));
    }

    @Test void savedValueReloadsAndFailedSaveLeavesLiveProtectionUnchanged() throws IOException {
        var file = temporary.resolve("config.yml").toFile();
        var key = BooleanConfigKey.PREVENT_NETHER_PORTAL_DESTRUCTION;
        var config = new ConfigurationService(file, new AtomicFileWriter(), Logger.getAnonymousLogger());
        assertFalse(config.get(key));
        assertTrue(config.update(key, true));
        var reload = new ConfigurationService(file, new AtomicFileWriter((from, to) -> {
            throw new IOException("expected test failure");
        }), Logger.getAnonymousLogger());
        assertTrue(reload.get(key));
        assertFalse(reload.update(key, false));
        assertTrue(reload.get(key));
        assertTrue(YamlConfiguration.loadConfiguration(file).getBoolean(key.configKey()));
    }
}
