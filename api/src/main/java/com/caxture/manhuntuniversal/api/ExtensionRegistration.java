package com.caxture.manhuntuniversal.api;

import java.util.Set;

/**
 * Opaque ownership token tied to the registering platform plugin. Tokens are checked by
 * object identity. The owner must close this token when finished; plugin disable also closes it.
 * Closing never restores former roles or revives participants.
 */
public interface ExtensionRegistration extends AutoCloseable {
    /**
     * Extension identifier supplied during registration.
     * @return the requested value or operation outcome
     */
    String id();
    /**
     * Immutable set of exclusive control domains owned by this token.
     * @return the requested value or operation outcome
     */
    Set<ControlDomain> controls();
    /**
     * Whether this token still belongs to the active service.
     * @return the requested value or operation outcome
     */
    boolean active();
    /** Releases ownership, idempotently. Throws IllegalStateException off the main thread. */
    @Override void close();
}
