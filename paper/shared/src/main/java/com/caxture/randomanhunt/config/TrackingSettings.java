package com.caxture.randomanhunt.config;

public record TrackingSettings(
        int retargetDistanceBlocks,
        int retargetCooldownSeconds,
        int searchRadiusBlocks
) {
}
