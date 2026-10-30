package com.caxture.manhuntuniversal.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConfigurationValues {
    private final EnumMap<IntConfigKey, Integer> integers;
    private final EnumMap<BooleanConfigKey, Boolean> booleans;

    private ConfigurationValues(
            Map<IntConfigKey, Integer> integers,
            Map<BooleanConfigKey, Boolean> booleans
    ) {
        this.integers = new EnumMap<>(integers);
        this.booleans = new EnumMap<>(booleans);
    }

    public static ConfigurationValues defaults() {
        EnumMap<IntConfigKey, Integer> integers = new EnumMap<>(IntConfigKey.class);
        for (IntConfigKey key : IntConfigKey.values()) {
            integers.put(key, key.defaultValue());
        }
        EnumMap<BooleanConfigKey, Boolean> booleans = new EnumMap<>(BooleanConfigKey.class);
        for (BooleanConfigKey key : BooleanConfigKey.values()) {
            booleans.put(key, key.defaultValue());
        }
        return new ConfigurationValues(integers, booleans);
    }

    public static ConfigurationValues fromRaw(Map<String, Object> raw, Consumer<String> warning) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(warning, "warning");
        ConfigurationValues defaults = defaults();
        EnumMap<IntConfigKey, Integer> integers = new EnumMap<>(IntConfigKey.class);
        EnumMap<BooleanConfigKey, Boolean> booleans = new EnumMap<>(BooleanConfigKey.class);

        for (IntConfigKey key : IntConfigKey.values()) {
            Object candidate = raw.get(key.configKey());
            if (isIntegerNumber(candidate)
                    && ((Number) candidate).longValue() >= Integer.MIN_VALUE
                    && ((Number) candidate).longValue() <= Integer.MAX_VALUE
                    && key.valid(((Number) candidate).intValue())) {
                integers.put(key, ((Number) candidate).intValue());
            } else {
                integers.put(key, defaults.get(key));
                warning.accept(key.configKey());
            }
        }
        for (BooleanConfigKey key : BooleanConfigKey.values()) {
            Object candidate = raw.get(key.configKey());
            if (candidate instanceof Boolean value) {
                booleans.put(key, value);
            } else {
                booleans.put(key, defaults.get(key));
                warning.accept(key.configKey());
            }
        }
        return new ConfigurationValues(integers, booleans);
    }

    private static boolean isIntegerNumber(Object candidate) {
        return candidate instanceof Byte
                || candidate instanceof Short
                || candidate instanceof Integer
                || candidate instanceof Long;
    }

    public int get(IntConfigKey key) {
        return integers.get(Objects.requireNonNull(key, "key"));
    }

    public boolean get(BooleanConfigKey key) {
        return booleans.get(Objects.requireNonNull(key, "key"));
    }

    public ConfigurationValues with(IntConfigKey key, int value) {
        Objects.requireNonNull(key, "key");
        if (!key.valid(value)) {
            throw new IllegalArgumentException("Value outside range for " + key.configKey());
        }
        EnumMap<IntConfigKey, Integer> updated = new EnumMap<>(integers);
        updated.put(key, value);
        return new ConfigurationValues(updated, booleans);
    }

    public ConfigurationValues with(BooleanConfigKey key, boolean value) {
        Objects.requireNonNull(key, "key");
        EnumMap<BooleanConfigKey, Boolean> updated = new EnumMap<>(booleans);
        updated.put(key, value);
        return new ConfigurationValues(integers, updated);
    }

    public TrackingSettings trackingSettings() {
        return new TrackingSettings(
                get(IntConfigKey.TRACKING_RETARGET_DISTANCE_BLOCKS),
                get(IntConfigKey.TRACKING_RETARGET_COOLDOWN_SECONDS),
                get(IntConfigKey.TRACKING_SEARCH_RADIUS_BLOCKS));
    }

    public RunnerStructureUnlockTimes runnerStructureUnlockTimes() {
        return new RunnerStructureUnlockTimes(
                get(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE),
                get(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_BASTION_REMNANT),
                get(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_NETHER_FORTRESS));
    }
}
