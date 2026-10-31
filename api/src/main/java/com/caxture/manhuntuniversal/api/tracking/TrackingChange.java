package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
public record TrackingChange(Kind kind, UUID playerId, String targetId, Optional<String> previousTargetId,
                             Optional<TargetAvailability> before, Optional<TargetAvailability> after, Source source) {
    public enum Kind { SELECTION, AVAILABILITY, LOCATION }
    public enum Source { NATURAL, CONFIGURATION, EXTENSION, REGISTRATION, PLAYER_STATE }
    public TrackingChange { Objects.requireNonNull(kind); Objects.requireNonNull(playerId); Objects.requireNonNull(targetId);
        Objects.requireNonNull(previousTargetId); Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(source); }
}
