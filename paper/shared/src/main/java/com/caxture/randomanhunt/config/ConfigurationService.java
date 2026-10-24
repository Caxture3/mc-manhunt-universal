package com.caxture.randomanhunt.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ConfigurationService {
    private final JavaPlugin plugin;
    private final File configFile;
    private final YamlConfiguration yaml;
    private final AtomicFileWriter writer;
    private ConfigurationValues values;

    public ConfigurationService(JavaPlugin plugin) {
        this(plugin, new AtomicFileWriter());
    }

    ConfigurationService(JavaPlugin plugin, AtomicFileWriter writer) {
        this.plugin = plugin;
        this.writer = writer;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        this.yaml = YamlConfiguration.loadConfiguration(configFile);
        this.values = load();
        persistMissingDefaults();
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
        return true;
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
        return true;
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
                plugin.getLogger().warning("Missing or invalid configuration value for " + key
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
            plugin.getLogger().severe("Could not save " + configFile + ": " + exception.getMessage());
            return false;
        }
    }
}
