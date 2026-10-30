package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Mutation outcome and an optional resulting current match snapshot.
 * @param status operation outcome
 * @param message human-readable outcome explanation
 * @param match detached immutable match snapshot
 */
public record OperationResult(OperationStatus status, String message, Optional<MatchSnapshot> match) {
    /** Creates a validated value; null components are rejected. */
    public OperationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(match, "match");
    }

    /**
     * Returns true exactly when the request was accepted.
     * @return the requested value or operation outcome
     */
    public boolean successful() { return status == OperationStatus.OK; }
}
