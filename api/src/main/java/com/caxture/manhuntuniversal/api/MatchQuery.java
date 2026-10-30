package com.caxture.manhuntuniversal.api;

import java.util.Optional;

/** Main-thread access to detached match snapshots. */
public interface MatchQuery {
    /**
     * Returns the active match, or empty while idle. Throws IllegalStateException off the main thread.
     * @return the requested value or operation outcome
     */
    Optional<MatchSnapshot> current();
}
