package com.caxture.manhuntuniversal.api;


/** Capabilities supported by an API service. */
public enum Capability {
    /** match query. */
    MATCH_QUERY,
    /** match control. */
    MATCH_CONTROL,
    /** role control. */
    ROLE_CONTROL,
    /** participant control. */
    PARTICIPANT_CONTROL,
    /** elimination policy. */
    ELIMINATION_POLICY,
    /** victory policy. */
    VICTORY_POLICY,
    /** paper events. */
    PAPER_EVENTS,
    CONFIGURATION_REGISTRY,
    FEATURE_POLICIES,
    TEXT_OVERRIDES,
    PRESENTATION_CONTROL,
    OBSERVER_IDENTITY,
    DYNAMIC_TRACKING,
    ASYNC_TRACKING_LOCATORS,
    TRACKING_EVENTS,
    FORCED_TRACKING,
    REALTIME_PLAYER_TRACKING
}
