package com.caxture.randomanhunt.tracking;

import com.caxture.randomanhunt.protocol.TrackingSelectionTarget;

public final class TrackingSelectionTargetMapper {
    private TrackingSelectionTargetMapper() {
    }

    public static TrackingSelectionTarget toProtocol(TrackingTargetId target) {
        return switch (target) {
            case NONE -> TrackingSelectionTarget.NONE;
            case VILLAGE -> TrackingSelectionTarget.VILLAGE;
            case NETHER_FORTRESS -> TrackingSelectionTarget.NETHER_FORTRESS;
            case BASTION_REMNANT -> TrackingSelectionTarget.BASTION_REMNANT;
        };
    }

    public static TrackingTargetId fromProtocol(TrackingSelectionTarget target) {
        return switch (target) {
            case NONE -> TrackingTargetId.NONE;
            case VILLAGE -> TrackingTargetId.VILLAGE;
            case NETHER_FORTRESS -> TrackingTargetId.NETHER_FORTRESS;
            case BASTION_REMNANT -> TrackingTargetId.BASTION_REMNANT;
        };
    }
}
