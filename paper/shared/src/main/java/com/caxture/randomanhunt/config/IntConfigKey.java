package com.caxture.randomanhunt.config;

import java.util.Arrays;
import java.util.Optional;

public enum IntConfigKey {
    RUNNER_COUNT("runner-count", 1, 1, 100),
    TRACKING_RETARGET_DISTANCE_BLOCKS("tracking-retarget-distance-blocks", 512, 64, 8192),
    TRACKING_RETARGET_COOLDOWN_SECONDS("tracking-retarget-cooldown-seconds", 30, 1, 600),
    TRACKING_SEARCH_RADIUS_BLOCKS("tracking-search-radius-blocks", 4096, 256, 16_384);

    private final String configKey;
    private final int defaultValue;
    private final int minimum;
    private final int maximum;

    IntConfigKey(String configKey, int defaultValue, int minimum, int maximum) {
        this.configKey = configKey;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public String configKey() {
        return configKey;
    }

    public int defaultValue() {
        return defaultValue;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }

    public boolean valid(int value) {
        return value >= minimum && value <= maximum;
    }

    public static Optional<IntConfigKey> find(String configKey) {
        return Arrays.stream(values()).filter(key -> key.configKey.equals(configKey)).findFirst();
    }
}
