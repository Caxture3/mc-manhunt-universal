package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.MutationContext;
import java.util.*;
public record TrackingMutation(MutationContext context, UUID playerId, long expectedDirectoryRevision) {
    public TrackingMutation { Objects.requireNonNull(context); Objects.requireNonNull(playerId);
        if (expectedDirectoryRevision < -1) throw new IllegalArgumentException("Invalid directory revision"); }
    public TrackingMutation(MutationContext context, UUID playerId) { this(context, playerId, -1); }
}
