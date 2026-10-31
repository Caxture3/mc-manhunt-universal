package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.*;
import java.time.Instant;
import java.util.*;
public record TrackingViewerContext(UUID playerId, String locale, WorldPosition position, String dimension,
                                    Optional<MatchSnapshot> match, Instant now) {
    public TrackingViewerContext { Objects.requireNonNull(playerId); Objects.requireNonNull(locale); Objects.requireNonNull(position);
        Objects.requireNonNull(dimension); Objects.requireNonNull(match); Objects.requireNonNull(now); }
}
