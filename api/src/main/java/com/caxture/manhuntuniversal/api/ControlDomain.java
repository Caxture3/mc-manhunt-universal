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
    VICTORY
}
