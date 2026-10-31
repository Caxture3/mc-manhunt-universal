package com.caxture.manhuntuniversal.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TrackingDistanceRefreshConfigurationTest {
    private static final DecimalConfigKey KEY = DecimalConfigKey.TRACKING_DISTANCE_REFRESH_SECONDS;
    @TempDir Path temporary;

    @Test void missingOrInvalidYamlUsesOneSecondAndWarns() {
        assertEquals(1.0, ConfigurationValues.defaults().get(KEY));
        for (Object invalid : List.of("0.5", Double.NaN, Double.POSITIVE_INFINITY, 0.099, 60.001, false)) {
            List<String> warnings = new ArrayList<>();
            assertEquals(1.0, ConfigurationValues.fromRaw(Map.of(KEY.configKey(), invalid), warnings::add).get(KEY));
            assertTrue(warnings.contains(KEY.configKey()));
        }
        assertEquals(1.0, ConfigurationValues.fromRaw(Map.of(), ignored -> {}).get(KEY));
    }

    @Test void acceptsFiniteNumbersWithinRangeAndRejectsInvalidUpdates() {
        for (Number valid : List.of(0.1, 0.35, 1, 60.0)) {
            assertEquals(valid.doubleValue(), ConfigurationValues.fromRaw(
                    Map.of(KEY.configKey(), valid), ignored -> {}).get(KEY));
        }
        for (double invalid : new double[]{0.0, 0.099, 60.001, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ConfigurationValues.defaults().with(KEY, invalid));
        }
        assertEquals(7, ConfigurationValues.defaults().with(KEY, 0.35).with(IntConfigKey.RUNNER_COUNT, 7)
                .with(BooleanConfigKey.FORCE_RANDOM_ROLES, true).get(IntConfigKey.RUNNER_COUNT));
        assertEquals(0.35, ConfigurationValues.defaults().with(KEY, 0.35).with(IntConfigKey.RUNNER_COUNT, 7)
                .with(BooleanConfigKey.FORCE_RANDOM_ROLES, true).get(KEY));
    }

    @Test void roundsSecondsUpToServerTicksWithoutFloatingPointOverrounding() {
        assertEquals(2L, KEY.refreshTicks(0.1));
        assertEquals(7L, KEY.refreshTicks(0.35));
        assertEquals(8L, KEY.refreshTicks(0.3501));
        assertEquals(20L, KEY.refreshTicks(1.0));
        assertEquals(1200L, KEY.refreshTicks(60.0));
    }

    @Test void persistsMissingDefaultAndDecimalChangesWithoutAffectingLiveValueOnSaveFailure() throws IOException {
        var file = temporary.resolve("config.yml").toFile();
        var config = new ConfigurationService(file, new AtomicFileWriter(), Logger.getAnonymousLogger());
        assertEquals(1.0, YamlConfiguration.loadConfiguration(file).getDouble(KEY.configKey()));
        assertTrue(config.update(KEY, 0.35));
        var reload = new ConfigurationService(file, new AtomicFileWriter((from, to) -> {
            throw new IOException("expected test failure");
        }), Logger.getAnonymousLogger());
        assertEquals(0.35, reload.get(KEY));
        assertFalse(reload.update(KEY, 0.1));
        assertEquals(0.35, reload.get(KEY));
        assertEquals(0.35, YamlConfiguration.loadConfiguration(file).getDouble(KEY.configKey()));
    }
}
