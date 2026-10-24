package com.caxture.randomanhunt.tracking;

import java.util.Arrays;
import java.util.Optional;

public enum TrackingCompassKind {
    RUNNER("runner"),
    HUNTER("hunter");

    private final String id;

    TrackingCompassKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<TrackingCompassKind> fromId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
