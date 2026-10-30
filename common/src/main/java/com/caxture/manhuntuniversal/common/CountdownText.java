package com.caxture.manhuntuniversal.common;

import java.util.Locale;

/** Locale-independent short labels for remaining unlock delays. */
public final class CountdownText {
    private CountdownText() {
    }

    public static String format(long remainingMillis) {
        if (remainingMillis >= 3_600_000) {
            return String.format(Locale.ROOT, "%.1fh", remainingMillis / 3_600_000.0);
        }
        if (remainingMillis >= 60_000) {
            return String.format(Locale.ROOT, "%.1fmin", remainingMillis / 60_000.0);
        }
        if (remainingMillis <= 0) {
            return "0s";
        }
        return ((remainingMillis + 999) / 1000) + "s";
    }
}
