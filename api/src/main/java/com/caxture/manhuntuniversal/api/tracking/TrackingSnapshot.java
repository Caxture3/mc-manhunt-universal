package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.WorldPosition;
import java.util.*;
/** Logical destination differs from guidance when a portal is used; distance follows guidance semantics. */
public record TrackingSnapshot(UUID playerId, Optional<String> targetId, TrackingStatus status, Optional<WorldPosition> destination,
                                Optional<WorldPosition> guidance, OptionalDouble distance, String source, String detail, long revision) {
    public TrackingSnapshot { Objects.requireNonNull(playerId); Objects.requireNonNull(targetId); Objects.requireNonNull(status);
        Objects.requireNonNull(destination); Objects.requireNonNull(guidance); Objects.requireNonNull(distance); Objects.requireNonNull(source); Objects.requireNonNull(detail); }
    public static TrackingSnapshot idle(UUID player, long revision) { return new TrackingSnapshot(player, Optional.empty(), TrackingStatus.IDLE,
            Optional.empty(), Optional.empty(), OptionalDouble.empty(), "", "", revision); }
}
