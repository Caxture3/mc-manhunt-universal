package com.caxture.manhuntuniversal.extension.config;

import com.caxture.manhuntuniversal.api.LocalizedText;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.config.*;
import java.util.*;
import java.util.function.*;

/** Registers existing core settings without changing their storage or application timing. */
public final class CoreConfigurationBindings {
    public CoreConfigurationBindings(ConfigurationService configuration, ConfigurationRegistryService registry,
                                     Function<String, LocalizedText> names) {
        for (var key : IntConfigKey.values()) {
            if (key.structureUnlockTime()) continue;
            ConfigTiming timing = key == IntConfigKey.NETHER_COORDINATE_SCALE ? ConfigTiming.IMMEDIATE
                    : key == IntConfigKey.RUNNER_COUNT || key.name().startsWith("TRACKING_") ? ConfigTiming.NEXT_MATCH : ConfigTiming.RUNNING_ONLY;
            registry.registerCore(definition(key.configKey(), names.apply(key.configKey()), key.defaultValue(),
                    ConfigTypes.integers(key.minimum(), key.maximum()), () -> configuration.savedValues().get(key),
                    value -> configuration.commitRaw(Map.of(key.configKey(), value)), timing, ""));
        }
        for (var key : BooleanConfigKey.values()) {
            ConfigTiming timing = switch (key) {
                case ALLOW_BLAZE_SPAWNER_BREAK, PREVENT_NETHER_PORTAL_DESTRUCTION, ADJUST_PIGLIN_BARTERING,
                        HUNTER_END_RESTRICTION_ENABLED, DISABLE_PIGLIN_BRUTES -> ConfigTiming.RUNNING_ONLY;
                case RESET_PLAYERS_AT_START -> ConfigTiming.NEXT_MATCH;
                default -> ConfigTiming.IMMEDIATE;
            };
            registry.registerCore(definition(key.configKey(), names.apply(key.configKey()), key.defaultValue(),
                    ConfigTypes.booleans(), () -> configuration.savedValues().get(key),
                    value -> configuration.commitRaw(Map.of(key.configKey(), value)), timing, ""));
        }
        for (var key : DecimalConfigKey.values()) registry.registerCore(definition(key.configKey(), names.apply(key.configKey()),
                key.defaultValue(), ConfigTypes.decimals(key.minimum(), key.maximum()), () -> configuration.savedValues().get(key),
                value -> configuration.commitRaw(Map.of(key.configKey(), value)), ConfigTiming.IMMEDIATE, "s"));
        Map<String, ConfigField<?>> fields = new LinkedHashMap<>();
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (var key : IntConfigKey.values()) if (key.structureUnlockTime()) {
            String leaf = key.configKey().substring(RunnerStructureUnlockTimes.CONFIG_KEY.length() + 1);
            fields.put(leaf, new ConfigField<>(LocalizedText.of(leaf), ConfigTypes.integers(-1, Integer.MAX_VALUE), "s"));
            defaults.put(leaf, key.defaultValue());
        }
        registry.registerCore(definition(RunnerStructureUnlockTimes.CONFIG_KEY, names.apply(RunnerStructureUnlockTimes.CONFIG_KEY + ".title"),
                defaults, ConfigTypes.group(fields), () -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    for (var key : IntConfigKey.values()) if (key.structureUnlockTime())
                        value.put(key.configKey().substring(RunnerStructureUnlockTimes.CONFIG_KEY.length() + 1), configuration.savedValues().get(key));
                    return value;
                }, value -> {
                    Map<String, Object> leaves = new LinkedHashMap<>();
                    value.forEach((key, item) -> leaves.put(RunnerStructureUnlockTimes.CONFIG_KEY + "." + key, item));
                    return configuration.commitRaw(leaves);
                }, ConfigTiming.IMMEDIATE, "s"));
        registry.onChange(configuration::effectiveChanged);
        configuration.bindRegistry(key -> registry.find(key).orElseThrow().nextMatchValue(),
                key->registry.find(key).orElseThrow().effectiveValue(),
                (key, value) -> registry.updateCommand(key, value).successful());
    }
    private static <T> ConfigDefinition<T> definition(String key, LocalizedText name, T fallback, ConfigType<T> type,
                                                      Supplier<T> reader, Predicate<T> writer, ConfigTiming timing, String unit) {
        return new ConfigDefinition<>(key, name, LocalizedText.of(""), fallback, type, new ConfigStorage<>() {
            public T read() { return reader.get(); }
            public boolean save(T value) { return writer.test(value); }
        }, timing, unit);
    }
}
