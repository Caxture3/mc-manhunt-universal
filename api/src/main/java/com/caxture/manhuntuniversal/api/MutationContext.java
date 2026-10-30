package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Mutation ownership and optimistic concurrency guard; -1 skips the revision check.
 * @param extension active opaque ownership token
 * @param sessionId identifier of the expected current match
 * @param expectedRevision expected revision, or -1 to skip revision validation
 * @param reason explanation of the change or result
 */
public record MutationContext(ExtensionRegistration extension, UUID sessionId, long expectedRevision, String reason) {
    /** Creates a validated value; null components are rejected. */
    public MutationContext {
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(sessionId, "sessionId");
        if (expectedRevision < -1) throw new IllegalArgumentException("expectedRevision must be -1 or non-negative");
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * Creates a request that checks the session identifier but not its revision.
     * @param extension active opaque ownership token
     * @param sessionId identifier of the expected current match
     * @param reason explanation of the change or result
     */
    public MutationContext(ExtensionRegistration extension, UUID sessionId, String reason) {
        this(extension, sessionId, -1, reason);
    }
}
