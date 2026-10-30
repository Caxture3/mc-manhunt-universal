package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tracks transitions per match, independently of compass/selection/role restarts. */
final class StructureUnlockAnnouncements {
    private UUID sessionId;
    private TrackingAvailability previous;

    void begin(UUID sessionId, TrackingAvailability initial) {
        if (!sessionId.equals(this.sessionId)) {
            this.sessionId = sessionId;
            previous = initial;
        }
    }

    List<TrackingTargetId> advance(TrackingAvailability current) {
        List<TrackingTargetId> unlocked = new ArrayList<>();
        if (previous != null) {
            for (TrackingTargetId target : TrackingTargetId.values()) {
                if (!target.selectableByRunner()) continue;
                var protocolTarget = TrackingSelectionTargetMapper.toProtocol(target);
                if (previous.remainingMillis(protocolTarget) > 0 && current.unlocked(protocolTarget)) {
                    unlocked.add(target);
                }
            }
        }
        previous = current;
        return List.copyOf(unlocked);
    }

    void clear() {
        sessionId = null;
        previous = null;
    }
}
