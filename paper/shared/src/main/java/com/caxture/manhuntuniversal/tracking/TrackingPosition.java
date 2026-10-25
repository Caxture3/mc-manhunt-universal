package com.caxture.manhuntuniversal.tracking;

import java.util.Objects;
import java.util.UUID;

public record TrackingPosition(
        UUID worldId,
        TrackingWorldKind worldKind,
        double x,
        double y,
        double z
) {
    public TrackingPosition {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldKind, "worldKind");
    }
}
