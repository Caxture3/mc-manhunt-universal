package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.*;
import java.util.*;
/** Detached trackable player. Empty role means a nonparticipant; position may be last known. @since 1.2 */
public record PlayerTrackingCandidate(UUID playerId, String name, boolean online, Optional<TeamRole> role, WorldPosition position) {
    public PlayerTrackingCandidate { Objects.requireNonNull(playerId); Objects.requireNonNull(name); Objects.requireNonNull(role); Objects.requireNonNull(position); }
}
