package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.Optional;

/**
 * A default, deferred, or replaced result. Only REPLACE carries a replacement.
 * @param action default, defer, or replace decision
 * @param replacement result present exactly for REPLACE
 */
public record VictoryDecision(Action action, Optional<Victory> replacement) {
    /** Available decisions before the default result is committed. */
    public enum Action {
        /** Keeps the proposed result. */
        DEFAULT,
        /** Leaves the match running. */
        DEFER,
        /** Uses an alternative result. */
        REPLACE
    }

    /** Creates a validated value; null components are rejected. */
    public VictoryDecision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(replacement, "replacement");
        if ((action == Action.REPLACE) != replacement.isPresent())
            throw new IllegalArgumentException("only REPLACE must carry a replacement");
    }

    /**
     * Keeps the core's proposed result.
     * @return the default decision
     */
    public static VictoryDecision defaults() { return new VictoryDecision(Action.DEFAULT, Optional.empty()); }

    /**
     * Leaves the match active without committing a result.
     * @return the defer decision
     */
    public static VictoryDecision defer() { return new VictoryDecision(Action.DEFER, Optional.empty()); }

    /**
     * Substitutes the supplied result.
     * @param victory replacement result
     * @return the replacement decision
     */
    public static VictoryDecision replace(Victory victory) { return new VictoryDecision(Action.REPLACE, Optional.of(victory)); }
}
