package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Fast main-thread callback at the configured distance cadence. No world scans or blocking I/O. @since 1.2 */
@FunctionalInterface public interface PlayerTrackingSelector {
    /** Returns an eligible candidate UUID, or empty for no target. Writes from this callback are rejected. */
    Optional<UUID> select(PlayerTrackingContext context);
}
