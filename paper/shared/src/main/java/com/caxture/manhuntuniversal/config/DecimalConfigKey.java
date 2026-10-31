package com.caxture.manhuntuniversal.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;

public enum DecimalConfigKey {
    TRACKING_DISTANCE_REFRESH_SECONDS("tracking-distance-refresh-seconds", 1.0, 0.1, 60.0);

    private final String configKey;
    private final double defaultValue;
    private final double minimum;
    private final double maximum;

    DecimalConfigKey(String configKey, double defaultValue, double minimum, double maximum) {
        this.configKey = configKey;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public String configKey() { return configKey; }
    public double defaultValue() { return defaultValue; }
    public double minimum() { return minimum; }
    public double maximum() { return maximum; }

    public boolean valid(double value) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    public long refreshTicks(double seconds) {
        if (!valid(seconds)) {
            throw new IllegalArgumentException("Value outside range for " + configKey);
        }
        return Math.max(2L, BigDecimal.valueOf(seconds).multiply(BigDecimal.valueOf(20))
                .setScale(0, RoundingMode.CEILING).longValueExact());
    }

    public static Optional<DecimalConfigKey> find(String configKey) {
        return Arrays.stream(values()).filter(key -> key.configKey.equals(configKey)).findFirst();
    }
}
