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
    PAPER_EVENTS
}
