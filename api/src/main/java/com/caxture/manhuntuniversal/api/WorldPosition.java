package com.caxture.manhuntuniversal.api;
import java.util.Objects;
import java.util.UUID;
/** Platform-free immutable coordinates. */
public record WorldPosition(UUID worldId, double x, double y, double z) {
    public WorldPosition {
        Objects.requireNonNull(worldId);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite coordinates");
    }
}
