package com.caxture.manhuntuniversal.api;

import java.util.UUID;

/** Main-thread participant mutations; wrong-thread calls return WRONG_THREAD. */
public interface ParticipantController {
    /**
     * Applies standard elimination, including policies, cancellable events, and spectator handling.
     * @param context ownership, session, and revision checks for this request
     * @param playerId affected participant identifier
     * @return the requested value or operation outcome
     */
    OperationResult eliminate(MutationContext context, UUID playerId);
    /**
     * Revives through standard state and presentation restoration.
     * @param context ownership, session, and revision checks for this request
     * @param playerId affected participant identifier
     * @return the requested value or operation outcome
     */
    OperationResult revive(MutationContext context, UUID playerId);
}
