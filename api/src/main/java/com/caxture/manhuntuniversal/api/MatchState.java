package com.caxture.manhuntuniversal.api;


/** Lifecycle of an existing match; idle is represented by an absent snapshot. */
public enum MatchState {
    /** countdown. */
    COUNTDOWN,
    /** running. */
    RUNNING,
    /** settling. */
    SETTLING
}
