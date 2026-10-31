package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
public record TrackingWorld(Mode mode, Optional<UUID> fixedWorldId) {
    public enum Mode { CURRENT, OVERWORLD, NETHER, END, FIXED }
    public TrackingWorld { Objects.requireNonNull(mode); Objects.requireNonNull(fixedWorldId);
        if ((mode == Mode.FIXED) != fixedWorldId.isPresent()) throw new IllegalArgumentException("FIXED requires a world UUID"); }
    public static TrackingWorld current() { return new TrackingWorld(Mode.CURRENT, Optional.empty()); }
    public static TrackingWorld nether() { return new TrackingWorld(Mode.NETHER, Optional.empty()); }
    public static TrackingWorld overworld() { return new TrackingWorld(Mode.OVERWORLD, Optional.empty()); }
    public static TrackingWorld fixed(UUID id) { return new TrackingWorld(Mode.FIXED, Optional.of(id)); }
}
