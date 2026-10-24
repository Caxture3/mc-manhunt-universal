package com.caxture.randomanhunt.protocol;

import java.util.Arrays;

public enum TrackingSelectionTarget {
    NONE(0),
    VILLAGE(1),
    NETHER_FORTRESS(2),
    BASTION_REMNANT(3);

    private final int wireId;

    TrackingSelectionTarget(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public boolean selectable() {
        return this != NONE;
    }

    public static TrackingSelectionTarget fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown tracking selection target: " + wireId));
    }
}
