package com.caxture.randomanhunt.config;

import java.util.Arrays;
import java.util.Optional;

public enum BooleanConfigKey {
    RUNNER_COMPASS_ENABLED("runner-compass-enabled", true),
    HUNTER_COMPASS_ENABLED("hunter-compass-enabled", true),
    RESET_PLAYERS_AT_START("reset-players-at-start", true),
    SHOW_CHAT_PREFIX("show-chat-prefix", true);

    private final String configKey;
    private final boolean defaultValue;

    BooleanConfigKey(String configKey, boolean defaultValue) {
        this.configKey = configKey;
        this.defaultValue = defaultValue;
    }

    public String configKey() {
        return configKey;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public static Optional<BooleanConfigKey> find(String configKey) {
        return Arrays.stream(values()).filter(key -> key.configKey.equals(configKey)).findFirst();
    }
}
