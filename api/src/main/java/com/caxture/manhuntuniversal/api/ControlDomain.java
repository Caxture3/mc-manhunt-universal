package com.caxture.manhuntuniversal.api;


/** Exclusive control domains held for the lifetime of a registration. */
public enum ControlDomain {
    /** match. */
    MATCH,
    /** roles. */
    ROLES,
    /** participants. */
    PARTICIPANTS,
    /** elimination. */
    ELIMINATION,
    /** victory. */
    VICTORY,
    /** Explicit ownership of role visibility rules used by presentation extensions. */
    ROLE_VISIBILITY,
    /** Exclusive forced tracking for all participants. @since 1.2 */
    TRACKING
}
