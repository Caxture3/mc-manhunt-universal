package com.caxture.manhuntuniversal.api;


/** Main-thread policy callback. Keep callbacks bounded and do not reenter mutation APIs. */
@FunctionalInterface
public interface EliminationPolicy {
    /**
     * DEFAULT eliminates runners for vanilla death and permits API elimination of either role.
     * PREVENT blocks elimination; ELIMINATE can also eliminate a hunter on death.
     * Callback failures conservatively prevent elimination.
     * @param context ownership, session, and revision checks for this request
     * @return the requested value or operation outcome
     */
    EliminationDecision decide(EliminationContext context);
}
