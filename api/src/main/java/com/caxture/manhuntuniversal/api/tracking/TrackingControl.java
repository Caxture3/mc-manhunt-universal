package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.*;
import java.util.*;
/** Main-thread exclusive TRACKING control for either role, including offline participants. @since 1.2 */
public interface TrackingControl {
    Optional<TrackingControlSnapshot> snapshot(UUID playerId);
    /** Bypasses manual menu availability/unlock and role compass switches. Core feature takeover still applies.
     * Closing the returned handle restores defaults only if this exact assignment remains current. */
    ExtensionResult<ExtensionHandle> apply(TrackingControlMutation mutation, TrackingOverrideRequest request);
}
