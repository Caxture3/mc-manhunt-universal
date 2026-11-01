package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Exactly one target source for TARGET; no source for DEFAULT/DISABLED. Not persisted. @since 1.2 */
public record TrackingOverrideRequest(TrackingOverrideMode mode, Optional<String> targetId,
                                      Optional<UUID> playerId, Optional<PlayerTrackingSelector> selector) {
    public TrackingOverrideRequest {
        Objects.requireNonNull(mode); Objects.requireNonNull(targetId); Objects.requireNonNull(playerId); Objects.requireNonNull(selector);
        int sources=(targetId.isPresent()?1:0)+(playerId.isPresent()?1:0)+(selector.isPresent()?1:0);
        if (sources!=(mode==TrackingOverrideMode.TARGET?1:0)) throw new IllegalArgumentException("Invalid override sources");
        if(targetId.filter(String::isBlank).isPresent())throw new IllegalArgumentException("Empty target ID");
    }
    public static TrackingOverrideRequest defaults(){return new TrackingOverrideRequest(TrackingOverrideMode.DEFAULT,Optional.empty(),Optional.empty(),Optional.empty());}
    public static TrackingOverrideRequest disabled(){return new TrackingOverrideRequest(TrackingOverrideMode.DISABLED,Optional.empty(),Optional.empty(),Optional.empty());}
    public static TrackingOverrideRequest target(String id){return new TrackingOverrideRequest(TrackingOverrideMode.TARGET,Optional.of(id),Optional.empty(),Optional.empty());}
    public static TrackingOverrideRequest player(UUID id){return new TrackingOverrideRequest(TrackingOverrideMode.TARGET,Optional.empty(),Optional.of(id),Optional.empty());}
    public static TrackingOverrideRequest players(PlayerTrackingSelector selector){return new TrackingOverrideRequest(TrackingOverrideMode.TARGET,Optional.empty(),Optional.empty(),Optional.of(selector));}
}
