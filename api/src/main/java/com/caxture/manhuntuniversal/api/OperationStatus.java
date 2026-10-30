package com.caxture.manhuntuniversal.api;


/** Outcome of a registration or mutation request. */
public enum OperationStatus {
    /** ok. */
    OK,
    /** wrong thread. */
    WRONG_THREAD,
    /** service unavailable. */
    SERVICE_UNAVAILABLE,
    /** invalid argument. */
    INVALID_ARGUMENT,
    /** no active match. */
    NO_ACTIVE_MATCH,
    /** stale session. */
    STALE_SESSION,
    /** stale revision. */
    STALE_REVISION,
    /** invalid state. */
    INVALID_STATE,
    /** invalid assignment. */
    INVALID_ASSIGNMENT,
    /** player unavailable. */
    PLAYER_UNAVAILABLE,
    /** cancelled. */
    CANCELLED,
    /** control conflict. */
    CONTROL_CONFLICT,
    /** unregistered extension. */
    UNREGISTERED_EXTENSION,
    /** busy. */
    BUSY,
    /** start rejected. */
    START_REJECTED
}
