package com.caxture.randomanhunt.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class ConfigurationService {
    private final Logger logger;
    private final File configFile;
    private final YamlConfiguration yaml;
    private final AtomicFileWriter writer;
    private ConfigurationValues values;
    private IntConsumer runnerCountChanged = ignored -> {};
    private Consumer<BooleanConfigKey> booleanChanged = ignored -> {};

    public ConfigurationService(JavaPlugin plugin) {
        this(plugin, new AtomicFileWriter());
    }

    ConfigurationService(JavaPlugin plugin, AtomicFileWriter writer) {
        this(prepareConfigFile(plugin), writer, plugin.getLogger());
    }

    ConfigurationService(File configFile, AtomicFileWriter writer, Logger logger) {
        this.logger = logger;
        this.writer = writer;
        this.configFile = configFile;
        this.yaml = YamlConfiguration.loadConfiguration(configFile);
        this.values = load();
        persistMissingDefaults();
    }

    private static File prepareConfigFile(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        return configFile;
    }

    public synchronized ConfigurationValues values() {
        return values;
    }

    public synchronized int get(IntConfigKey key) {
        return values.get(key);
    }

    public synchronized boolean get(BooleanConfigKey key) {
        return values.get(key);
    }

    public synchronized boolean showChatPrefix() {
        return get(BooleanConfigKey.SHOW_CHAT_PREFIX);
    }

    public synchronized boolean resetPlayersAtStart() {
        return get(BooleanConfigKey.RESET_PLAYERS_AT_START);
    }

    public synchronized boolean update(IntConfigKey key, int value) {
        ConfigurationValues updated = values.with(key, value);
        Object previousRaw = yaml.get(key.configKey());
        yaml.set(key.configKey(), value);
        if (!save()) {
            yaml.set(key.configKey(), previousRaw);
            return false;
        }
        values = updated;
        if (key == IntConfigKey.RUNNER_COUNT) runnerCountChanged.accept(value);
        return true;
    }

    public synchronized void onRunnerCountChanged(IntConsumer listener) {
        runnerCountChanged = Objects.requireNonNull(listener);
    }

    public synchronized boolean update(BooleanConfigKey key, boolean value) {
        ConfigurationValues updated = values.with(key, value);
        Object previousRaw = yaml.get(key.configKey());
        yaml.set(key.configKey(), value);
        if (!save()) {
            yaml.set(key.configKey(), previousRaw);
            return false;
        }
        values = updated;
        booleanChanged.accept(key);
        return true;
    }

    public synchronized void onBooleanChanged(Consumer<BooleanConfigKey> listener) {
        booleanChanged = Objects.requireNonNull(listener);
    }

    private ConfigurationValues load() {
        Map<String, Object> raw = new HashMap<>();
        for (IntConfigKey key : IntConfigKey.values()) {
            raw.put(key.configKey(), yaml.get(key.configKey()));
        }
        for (BooleanConfigKey key : BooleanConfigKey.values()) {
            raw.put(key.configKey(), yaml.get(key.configKey()));
        }
        Set<String> warned = new HashSet<>();
        return ConfigurationValues.fromRaw(raw, key -> {
            if (warned.add(key)) {
                logger.warning("Missing or invalid configuration value for " + key
                        + "; using the default value.");
            }
        });
    }

    private void persistMissingDefaults() {
        boolean changed = false;
        for (IntConfigKey key : IntConfigKey.values()) {
            if (!yaml.contains(key.configKey())) {
                yaml.set(key.configKey(), key.defaultValue());
                changed = true;
            }
        }
        for (BooleanConfigKey key : BooleanConfigKey.values()) {
            if (!yaml.contains(key.configKey())) {
                yaml.set(key.configKey(), key.defaultValue());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private boolean save() {
        try {
            writer.replace(configFile.toPath(), yaml.saveToString());
            return true;
        } catch (IOException exception) {
            logger.severe("Could not save " + configFile + ": " + exception.getMessage());
            return false;
        }
    }
}
