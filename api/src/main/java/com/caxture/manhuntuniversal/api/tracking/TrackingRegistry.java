package com.caxture.manhuntuniversal.api.tracking;

/** Main-thread target catalog, authorization and locating service. */
public interface TrackingRegistry {
    /** Optional since 1.2; check FORCED_TRACKING capability first. */
    default TrackingControl control() { throw new UnsupportedOperationException("Tracking control requires API 1.2"); }
    java.util.List<TrackingTargetInfo> list();
    java.util.Optional<TrackingMenuSnapshot> menu(java.util.UUID playerId);
    java.util.Optional<TrackingSnapshot> snapshot(java.util.UUID playerId);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> register(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, TrackingTargetDefinition definition);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> replaceLocator(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String targetId, int priority, TrackingLocator locator);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> availability(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String targetId, int priority, TrackingAvailabilityPolicy policy);
    com.caxture.manhuntuniversal.api.ExtensionResult<TrackingSnapshot> select(TrackingMutation mutation, String targetId);
    com.caxture.manhuntuniversal.api.ExtensionResult<TrackingSnapshot> clear(TrackingMutation mutation);
    com.caxture.manhuntuniversal.api.ExtensionResult<TrackingSnapshot> refresh(TrackingMutation mutation);
    com.caxture.manhuntuniversal.api.ExtensionResult<Void> invalidate(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, TrackingInvalidation scope);
}
