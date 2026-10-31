package com.caxture.manhuntuniversal.api.tracking;
import java.util.Objects;
/** remainingMillis=0 can also represent a task lock; selectable is always authoritative. */
public record TargetAvailability(boolean visible, boolean selectable, long remainingMillis, String reason) {
    public TargetAvailability { Objects.requireNonNull(reason);
        if (remainingMillis < -1 || !visible && selectable || selectable && remainingMillis != 0) throw new IllegalArgumentException("Invalid availability"); }
    public static TargetAvailability available() { return new TargetAvailability(true, true, 0, ""); }
    public static TargetAvailability hidden() { return new TargetAvailability(false, false, -1, ""); }
    public static TargetAvailability locked(long millis, String reason) { return new TargetAvailability(true, false, Math.max(0, millis), reason); }
    public boolean sameState(TargetAvailability other) { return visible == other.visible && selectable == other.selectable
            && (remainingMillis > 0) == (other.remainingMillis > 0) && reason.equals(other.reason); }
}
