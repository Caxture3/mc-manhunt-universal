package com.caxture.manhuntuniversal.config;

import java.util.Arrays;
import java.util.Optional;

public enum BooleanConfigKey {
    FORCE_RANDOM_ROLES("force-random-roles", false),
    HIDE_ROLE_ASSIGNMENTS("hide-role-assignments", false),
    RUNNER_COMPASS_ENABLED("runner-compass-enabled", true),
    HUNTER_COMPASS_ENABLED("hunter-compass-enabled", true),
    RESET_PLAYERS_AT_START("reset-players-at-start", true),
    SHOW_CHAT_PREFIX("show-chat-prefix", true),
    ALLOW_BLAZE_SPAWNER_BREAK("allow-blaze-spawner-break", false),
    ADJUST_PIGLIN_BARTERING("adjust-piglin-bartering", false),
    HUNTER_END_RESTRICTION_ENABLED("hunter-end-restriction-enabled", false);

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
