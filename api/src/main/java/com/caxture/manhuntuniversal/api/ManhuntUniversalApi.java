package com.caxture.manhuntuniversal.api;

import java.util.Set;

/**
 * Public extension service. Obtain this service from the platform's service registry.
 * All live queries and mutations require the server main thread. Snapshots are immutable.
 * Mutation failures are returned as statuses; reentrant mutation requests return BUSY.
 */
public interface ManhuntUniversalApi {
    /**
     * Independent semantic version of this extension API.
     * @return the requested value or operation outcome
     */
    String apiVersion();
    /**
     * Immutable supported capability set.
     * @return the requested value or operation outcome
     */
    Set<Capability> capabilities();
    /**
     * Access to detached match snapshots.
     * @return the requested value or operation outcome
     */
    MatchQuery matches();
    /**
     * Access to lifecycle control; requires MATCH ownership.
     * @return the requested value or operation outcome
     */
    MatchController control();
    /**
     * Access to atomic role changes; requires ROLES ownership.
     * @return the requested value or operation outcome
     */
    RoleController roles();
    /**
     * Access to elimination and revival; requires PARTICIPANTS ownership.
     * @return the requested value or operation outcome
     */
    ParticipantController participants();
}
