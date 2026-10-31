package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.WorldPosition;
import java.util.*;
public record LocateResult(Status status, Optional<WorldPosition> position, String source, String detail) {
    public enum Status { FOUND, NOT_FOUND, USE_CORE, FAILED }
    public LocateResult { Objects.requireNonNull(status); Objects.requireNonNull(position); Objects.requireNonNull(source); Objects.requireNonNull(detail);
        if ((status == Status.FOUND) != position.isPresent()) throw new IllegalArgumentException("Only FOUND carries a position"); }
    public static LocateResult found(WorldPosition position, String source) { return new LocateResult(Status.FOUND, Optional.of(position), source, ""); }
    public static LocateResult notFound(String detail) { return new LocateResult(Status.NOT_FOUND, Optional.empty(), "", detail); }
    public static LocateResult core() { return new LocateResult(Status.USE_CORE, Optional.empty(), "core", ""); }
    public static LocateResult failed(String detail) { return new LocateResult(Status.FAILED, Optional.empty(), "", detail); }
}
