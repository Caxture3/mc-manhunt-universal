package com.caxture.manhuntuniversal.api;

import java.util.Map;
import java.util.UUID;

/** Main-thread atomic role mutations; wrong-thread calls return WRONG_THREAD. */
public interface RoleController {
    /**
     * Assigns every participant exactly once. Eliminated roles must remain unchanged.
     * Changed players must be online and available; the match must retain a living runner and hunter.
     * Rejection or event cancellation leaves roles unchanged.
     * @param context ownership, session, and revision checks for this request
     * @param assignments complete participant-to-role assignment
     * @return the requested value or operation outcome
     */
    OperationResult assign(MutationContext context, Map<UUID, TeamRole> assignments);
    /**
     * Randomizes available living participants, retaining offline and eliminated roles.
     * runnerCount is the final number of uneliminated runners, including fixed offline runners.
     * @param context ownership, session, and revision checks for this request
     * @param runnerCount final number of living runners including fixed offline runners
     * @return the requested value or operation outcome
     */
    OperationResult reroll(MutationContext context, int runnerCount);
}
