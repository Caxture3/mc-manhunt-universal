package com.caxture.manhuntuniversal.game;

public record TrackingSettingsSnapshot(
        int retargetDistanceBlocks,
        int retargetCooldownSeconds,
        int searchRadiusBlocks
) {
    public TrackingSettingsSnapshot {
        if (retargetDistanceBlocks < 1 || retargetCooldownSeconds < 1 || searchRadiusBlocks < 1) {
            throw new IllegalArgumentException("Tracking settings must be positive");
        }
    }
}
