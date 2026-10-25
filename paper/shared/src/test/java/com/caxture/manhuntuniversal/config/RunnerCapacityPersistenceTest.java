package com.caxture.manhuntuniversal.config;

import com.caxture.manhuntuniversal.game.TeamPreference;
import com.caxture.manhuntuniversal.game.TeamSelectionService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RunnerCapacityPersistenceTest {
    @TempDir Path temporary;

    @Test
    void failedAtomicSaveKeepsTheFileValueAndAllRunnerChoices() throws IOException {
        verifySave(false);
    }

    @Test
    void successfulAtomicSaveClearsOnlyTheLatestExcessRunner() throws IOException {
        verifySave(true);
    }

    private void verifySave(boolean successful) throws IOException {
        var file = temporary.resolve("config.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        for (IntConfigKey key : IntConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        for (BooleanConfigKey key : BooleanConfigKey.values()) yaml.set(key.configKey(), key.defaultValue());
        yaml.set("runner-count", 2);
        yaml.save(file);
        AtomicFileWriter writer = successful ? new AtomicFileWriter()
                : new AtomicFileWriter((from, to) -> { throw new IOException("Test write failure"); });
        ConfigurationService configuration = new ConfigurationService(file, writer, Logger.getAnonymousLogger());
        TeamSelectionService teams = new TeamSelectionService(() -> false);
        UUID first = new UUID(0, 1), last = new UUID(0, 2);
        teams.choose(first, TeamPreference.RUNNER, 2);
        teams.choose(last, TeamPreference.RUNNER, 2);
        configuration.onRunnerCountChanged(teams::trimRunners);

        assertEquals(successful, configuration.update(IntConfigKey.RUNNER_COUNT, 1));
        assertEquals(successful ? 1 : 2, configuration.get(IntConfigKey.RUNNER_COUNT));
        assertEquals(successful ? 1 : 2, YamlConfiguration.loadConfiguration(file).getInt("runner-count"));
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(first));
        assertEquals(successful ? TeamPreference.RANDOM : TeamPreference.RUNNER, teams.preferenceOf(last));
    }
}
