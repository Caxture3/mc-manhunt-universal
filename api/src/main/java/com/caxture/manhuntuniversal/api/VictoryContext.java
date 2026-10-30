package com.caxture.manhuntuniversal.api;

import java.util.Objects;

/**
 * Immutable policy input before the default result is committed.
 * @param match detached immutable match snapshot
 * @param proposed candidate result or complete role assignment
 */
public record VictoryContext(MatchSnapshot match, Victory proposed) {
    /** Creates a validated value; null components are rejected. */
    public VictoryContext {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(proposed, "proposed");
    }
}
