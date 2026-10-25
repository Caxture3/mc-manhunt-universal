package com.caxture.manhuntuniversal.config;

public record TrackingSettings(
        int retargetDistanceBlocks,
        int retargetCooldownSeconds,
        int searchRadiusBlocks
) {
}
