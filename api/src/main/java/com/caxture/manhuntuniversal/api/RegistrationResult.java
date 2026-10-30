package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of an extension registration attempt.
 * @param status operation outcome
 * @param registration successful ownership token, when present
 * @param message human-readable outcome explanation
 */
public record RegistrationResult(OperationStatus status, Optional<ExtensionRegistration> registration, String message) {
    /** Creates a validated value; null components are rejected. */
    public RegistrationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(message, "message");
    }
}
