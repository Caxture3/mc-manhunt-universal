package com.caxture.manhuntuniversal.tracking;

import java.util.Arrays;
import java.util.Optional;
import java.util.List;

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

    List<String> loreKeys(boolean controlled) {
        if (controlled) {
            return List.of("tracking.compass.lore-bound");
        }
        String instruction = this == RUNNER ? "tracking.compass.lore-runner-use" : "tracking.compass.lore-hunter-use";
        return List.of(instruction, "tracking.compass.lore-bound");
    }

    public static Optional<TrackingCompassKind> fromId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
