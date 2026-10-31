package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
public record TrackingInvalidation(Optional<String> targetId, Optional<UUID> playerId, boolean availabilityChanged) {
    public TrackingInvalidation { Objects.requireNonNull(targetId); Objects.requireNonNull(playerId); }
    public static TrackingInvalidation target(String id) { return new TrackingInvalidation(Optional.of(id), Optional.empty(), false); }
}
