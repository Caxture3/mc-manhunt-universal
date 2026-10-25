package com.caxture.manhuntuniversal.tracking;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TrackingTargetId {
    VILLAGE("village", TrackingWorldKind.OVERWORLD),
    NETHER_FORTRESS("nether-fortress", TrackingWorldKind.NETHER),
    BASTION_REMNANT("bastion-remnant", TrackingWorldKind.NETHER),
    NONE("none", null);

    private final String id;
    private final TrackingWorldKind targetWorldKind;

    TrackingTargetId(String id, TrackingWorldKind targetWorldKind) {
        this.id = id;
        this.targetWorldKind = targetWorldKind;
    }

    public String id() {
        return id;
    }

    public String languageKey() {
        return "tracking.target." + id;
    }

    public Optional<TrackingWorldKind> targetWorldKind() {
        return Optional.ofNullable(targetWorldKind);
    }

    public boolean selectableByRunner() {
        return this != NONE;
    }

    public static Optional<TrackingTargetId> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(target -> target.id.equals(normalized)).findFirst();
    }
}
