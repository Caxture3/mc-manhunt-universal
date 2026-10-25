package com.caxture.randomanhunt.config;

import com.caxture.randomanhunt.game.TeamPreference;
import com.caxture.randomanhunt.game.TeamSelectionService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class RoleOptionsPersistenceTest {
    @TempDir Path temporary;

    @Test void failedSavePreservesSelectionsAndNeverRunsDisplayCallbacks() throws IOException { verify(false); }
    @Test void successfulSaveClearsSelectionsWhileHideRemainsIndependent() throws IOException { verify(true); }

    private void verify(boolean successful) throws IOException {
        var file = temporary.resolve("config.yml").toFile();
        var yaml = new YamlConfiguration();
        for (IntConfigKey key : IntConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        for (BooleanConfigKey key : BooleanConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        yaml.save(file);
        AtomicFileWriter writer = successful ? new AtomicFileWriter()
                : new AtomicFileWriter((from, to) -> { throw new IOException("Test write failure"); });
        ConfigurationService config = new ConfigurationService(file, writer, Logger.getAnonymousLogger());
        TeamSelectionService selections = new TeamSelectionService(() -> false);
        UUID player = UUID.randomUUID();
        selections.choose(player, TeamPreference.RUNNER, 1);
        int[] calls = {0};
        config.onBooleanChanged(key -> {
            calls[0]++;
            if (key == BooleanConfigKey.FORCE_RANDOM_ROLES) {
                selections.setForcedRandom(config.get(key));
            }
        });
        assertFalse(config.get(BooleanConfigKey.FORCE_RANDOM_ROLES));
        assertFalse(config.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS));
        assertEquals(successful, config.update(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS, true));
        assertEquals(TeamPreference.RUNNER, selections.preferenceOf(player));
        assertFalse(selections.forcedRandom());
        assertEquals(successful, config.update(BooleanConfigKey.FORCE_RANDOM_ROLES, true));
        assertEquals(successful ? TeamPreference.RANDOM : TeamPreference.RUNNER, selections.preferenceOf(player));
        assertEquals(successful ? 2 : 0, calls[0]);
        assertEquals(successful, config.get(BooleanConfigKey.FORCE_RANDOM_ROLES));
        assertEquals(successful, config.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS));
        assertEquals(successful, YamlConfiguration.loadConfiguration(file).getBoolean("force-random-roles"));
        if (successful) {
            config.update(BooleanConfigKey.FORCE_RANDOM_ROLES, false);
            assertTrue(config.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS));
            assertTrue(selections.snapshot().isEmpty());
        }
    }
}
