package com.caxture.manhuntuniversal.config;

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
    private Runnable structureUnlockTimesChanged = () -> {};
    private java.util.function.Function<String, Object> effectiveValue;
    private java.util.function.Function<String,Object> activeValue;
    private java.util.function.BiFunction<String, Object, Boolean> registryWrite;
    private com.caxture.manhuntuniversal.extension.feature.CoreFeatureAccess features;
    public void featureAccess(com.caxture.manhuntuniversal.extension.feature.CoreFeatureAccess features) { this.features = features; }
    public boolean coreEffect(String feature, org.bukkit.entity.Entity actor, org.bukkit.Location position, String operation) {
        return features == null || features.allows(feature, actor, position, operation);
    }

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
        if (effectiveValue == null) return values;
        ConfigurationValues snapshot = values;
        for (var key : IntConfigKey.values()) snapshot = snapshot.with(key, get(key));
        for (var key : BooleanConfigKey.values()) snapshot = snapshot.with(key, get(key));
        for (var key : DecimalConfigKey.values()) snapshot = snapshot.with(key, get(key));
        return snapshot;
    }

    public synchronized ConfigurationValues savedValues() { return values; }
    public synchronized void bindRegistry(java.util.function.Function<String, Object> reader,
                                         java.util.function.BiFunction<String, Object, Boolean> writer) {
        bindRegistry(reader,reader,writer);
    }
    public synchronized void bindRegistry(java.util.function.Function<String,Object> reader,java.util.function.Function<String,Object> active,
                                         java.util.function.BiFunction<String,Object,Boolean> writer){
        effectiveValue=reader;activeValue=active;registryWrite=writer;
    }
    public synchronized ConfigurationValues matchValues(){
        if(activeValue==null)return values();
        ConfigurationValues snapshot=values;
        for(var key:IntConfigKey.values())snapshot=snapshot.with(key,((Number)activeValue.apply(key.configKey())).intValue());
        for(var key:BooleanConfigKey.values())snapshot=snapshot.with(key,(Boolean)activeValue.apply(key.configKey()));
        for(var key:DecimalConfigKey.values())snapshot=snapshot.with(key,((Number)activeValue.apply(key.configKey())).doubleValue());
        return snapshot;
    }

    public synchronized int get(IntConfigKey key) {
        return effectiveValue == null ? values.get(key) : ((Number) effectiveValue.apply(key.configKey())).intValue();
    }

    public synchronized boolean get(BooleanConfigKey key) {
        return effectiveValue == null ? values.get(key) : (Boolean) effectiveValue.apply(key.configKey());
    }

    public synchronized double get(DecimalConfigKey key) {
        return effectiveValue == null ? values.get(key) : ((Number) effectiveValue.apply(key.configKey())).doubleValue();
    }

    public synchronized boolean showChatPrefix() {
        return get(BooleanConfigKey.SHOW_CHAT_PREFIX);
    }

    public synchronized boolean resetPlayersAtStart() {
        return get(BooleanConfigKey.RESET_PLAYERS_AT_START);
    }

    public synchronized boolean update(IntConfigKey key, int value) {
        if (registryWrite != null) return registryWrite.apply(key.configKey(), value);
        ConfigurationValues updated = values.with(key, value);
        Object previousRaw = yaml.get(key.configKey());
        yaml.set(key.configKey(), value);
        if (!save()) {
            yaml.set(key.configKey(), previousRaw);
            return false;
        }
        values = updated;
        if (key == IntConfigKey.RUNNER_COUNT) runnerCountChanged.accept(value);
        if (key.structureUnlockTime()) structureUnlockTimesChanged.run();
        return true;
    }

    public synchronized void onRunnerCountChanged(IntConsumer listener) {
        runnerCountChanged = Objects.requireNonNull(listener);
    }

    public synchronized boolean update(BooleanConfigKey key, boolean value) {
        if (registryWrite != null) return registryWrite.apply(key.configKey(), value);
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

    public synchronized boolean update(DecimalConfigKey key, double value) {
        if (registryWrite != null) return registryWrite.apply(key.configKey(), value);
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

    public synchronized void onStructureUnlockTimesChanged(Runnable listener) {
        structureUnlockTimesChanged = Objects.requireNonNull(listener);
    }

    /** Internal atomic persistence gateway. Registry publishes effective changes after this succeeds. */
    public synchronized boolean commitRaw(Map<String, Object> changes) {
        ConfigurationValues candidate = values;
        for (var entry : changes.entrySet()) {
            var integer = IntConfigKey.find(entry.getKey()).orElse(null);
            var bool = BooleanConfigKey.find(entry.getKey()).orElse(null);
            var decimal = DecimalConfigKey.find(entry.getKey()).orElse(null);
            if (integer != null) candidate = candidate.with(integer, ((Number) entry.getValue()).intValue());
            else if (bool != null) candidate = candidate.with(bool, (Boolean) entry.getValue());
            else if (decimal != null) candidate = candidate.with(decimal, ((Number) entry.getValue()).doubleValue());
            else throw new IllegalArgumentException("Unknown core key: " + entry.getKey());
        }
        Map<String, Object> old = new HashMap<>();
        changes.forEach((key, value) -> { old.put(key, yaml.get(key)); yaml.set(key, value); });
        if (!save()) { old.forEach(yaml::set); return false; }
        values = candidate;
        return true;
    }
    public synchronized void effectiveChanged(Set<String> keys) {
        if (keys.contains(IntConfigKey.RUNNER_COUNT.configKey())) runnerCountChanged.accept(get(IntConfigKey.RUNNER_COUNT));
        for (var key : BooleanConfigKey.values()) if (keys.contains(key.configKey())) booleanChanged.accept(key);
        if (keys.contains(RunnerStructureUnlockTimes.CONFIG_KEY) || keys.stream().anyMatch(key -> key.startsWith(RunnerStructureUnlockTimes.CONFIG_KEY + ".")))
            structureUnlockTimesChanged.run();
    }

    private ConfigurationValues load() {
        Map<String, Object> raw = new HashMap<>();
        for (IntConfigKey key : IntConfigKey.values()) {
            raw.put(key.configKey(), yaml.get(key.configKey()));
        }
        for (BooleanConfigKey key : BooleanConfigKey.values()) {
            raw.put(key.configKey(), yaml.get(key.configKey()));
        }
        for (DecimalConfigKey key : DecimalConfigKey.values()) {
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
        for (DecimalConfigKey key : DecimalConfigKey.values()) {
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
