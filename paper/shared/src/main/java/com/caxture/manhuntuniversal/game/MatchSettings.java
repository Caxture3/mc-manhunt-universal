package com.caxture.manhuntuniversal.game;

import java.util.Objects;

public record MatchSettings(
        int runnerCount,
        boolean resetPlayersAtStart,
        TrackingSettingsSnapshot trackingSettings
) {
    public MatchSettings {
        if (runnerCount < 1 || runnerCount > 100) {
            throw new IllegalArgumentException("Runner count is outside 1..100");
        }
        Objects.requireNonNull(trackingSettings, "trackingSettings");
    }
}
