package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.game.TrackingSettingsSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class TrackingPolicy {
    private TrackingPolicy() {
    }

    public static long roundedDisplayDistance(TrackingPosition first, TrackingPosition second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.round(Math.sqrt(x * x + y * y + z * z));
    }

    public static double horizontalDistance(TrackingPosition first, TrackingPosition second) {
        double x = first.x() - second.x();
        double z = first.z() - second.z();
        return Math.hypot(x, z);
    }

    public static boolean shouldRetarget(
            TrackingPosition searchOrigin,
            TrackingPosition current,
            Instant searchedAt,
            Instant now,
            TrackingSettingsSnapshot settings
    ) {
        return searchOrigin.worldId().equals(current.worldId())
                && horizontalDistance(searchOrigin, current) >= settings.retargetDistanceBlocks()
                && Duration.between(searchedAt, now).compareTo(
                Duration.ofSeconds(settings.retargetCooldownSeconds())) >= 0;
    }

    public static boolean shouldInvalidateForTeleport(
            TrackingPosition from,
            TrackingPosition to
    ) {
        return !from.worldId().equals(to.worldId());
    }

    public static Optional<HunterTrackingTarget> nearestRunner(
            TrackingPosition origin,
            List<TrackingPlayerCandidate> runnersInParticipantOrder
    ) {
        HunterTrackingTarget live = nearest(origin, runnersInParticipantOrder, true);
        return live == null
                ? Optional.ofNullable(nearest(origin, runnersInParticipantOrder, false))
                : Optional.of(live);
    }

    private static HunterTrackingTarget nearest(
            TrackingPosition origin,
            List<TrackingPlayerCandidate> candidates,
            boolean liveSameWorld
    ) {
        HunterTrackingTarget best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TrackingPlayerCandidate candidate : candidates) {
            Optional<TrackingPosition> direction = liveSameWorld
                    ? liveDirection(origin, candidate)
                    : fallbackDirection(origin, candidate);
            if (direction.isEmpty()) {
                continue;
            }
            double distance = horizontalDistance(origin, direction.orElseThrow());
            if (distance < bestDistance) {
                best = new HunterTrackingTarget(candidate, direction.orElseThrow(), liveSameWorld);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Optional<TrackingPosition> liveDirection(
            TrackingPosition origin,
            TrackingPlayerCandidate candidate
    ) {
        if (!candidate.online()) {
            return Optional.empty();
        }
        return candidate.actualPosition()
                .filter(position -> position.worldId().equals(origin.worldId()));
    }

    private static Optional<TrackingPosition> fallbackDirection(
            TrackingPosition origin,
            TrackingPlayerCandidate candidate
    ) {
        return candidate.lastPositionInViewerWorld()
                .filter(position -> position.worldId().equals(origin.worldId()));
    }
}
