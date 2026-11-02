package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.api.WorldPosition;
import com.caxture.manhuntuniversal.api.tracking.*;
import java.time.Duration;
import java.time.Instant;

/** Main-thread custom query state, including cooldown retained after a projection invalidation. */
final class TrackingLocateQuery {
    final LocateRequest request;
    final Instant submittedAt;
    final WorldPosition movement;
    boolean pending = true, refresh;
    LocateResult result;
    TrackingLocateQuery(LocateRequest request, Instant submittedAt, WorldPosition movement) {
        this.request = request; this.submittedAt = submittedAt; this.movement = movement;
    }
    boolean ready(Instant now, int cooldownSeconds, boolean moved, boolean sameViewerWorld) {
        return !pending && Duration.between(submittedAt, now).getSeconds() >= cooldownSeconds
                && (refresh || moved || !sameViewerWorld);
    }
    boolean current(LocateRequest candidate) {
        return request.requestVersion() == candidate.requestVersion() && request.sessionId().equals(candidate.sessionId());
    }
    TrackingLocateQuery invalidated(long version) {
        var replacement = new LocateRequest(request.sessionId(), request.targetRevision(), version, request.playerId(), request.targetId(),
                request.viewer(), request.origin(), request.match(), request.radiusBlocks());
        var next = new TrackingLocateQuery(replacement, submittedAt, movement);
        next.pending = false; next.refresh = true;
        return next;
    }
}
