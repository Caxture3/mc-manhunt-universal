package com.caxture.manhuntuniversal.api;

import java.util.Objects;

/**
 * A proposed result and an extension-provided explanation.
 * @param winner winning side
 * @param reason explanation of the change or result
 */
public record Victory(MatchWinner winner, String reason) {
    /** Creates a validated value; null components are rejected. */
    public Victory {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(reason, "reason");
    }
}
