package com.caxture.manhuntuniversal.config;

import java.util.Arrays;
import java.util.Optional;

public enum IntConfigKey {
    RUNNER_COUNT("runner-count", 1, 1, 100),
    ORE_FORTUNE_LEVEL("ore-fortune-level", 0, 0, 10),
    RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE("runner-structure-tracker-unlock-time.village", 0, -1, Integer.MAX_VALUE),
    RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_BASTION_REMNANT("runner-structure-tracker-unlock-time.bastion_remnant", 0, -1, Integer.MAX_VALUE),
    RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_NETHER_FORTRESS("runner-structure-tracker-unlock-time.nether_fortress", 0, -1, Integer.MAX_VALUE),
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

    public boolean structureUnlockTime() {
        return configKey.startsWith(RunnerStructureUnlockTimes.CONFIG_KEY + ".");
    }

    public static Optional<IntConfigKey> find(String configKey) {
        return Arrays.stream(values()).filter(key -> key.configKey.equals(configKey)).findFirst();
    }
}
