package com.caxture.manhuntuniversal.api;


/** Main-thread lifecycle requests. Wrong-thread calls return WRONG_THREAD. */
public interface MatchController {
    /**
     * Starts through the normal roster, reset, and countdown checks; countdownSeconds must be between 0 and 60.
     * @param extension active opaque ownership token
     * @param countdownSeconds countdown duration in seconds, from 0 to 60 inclusive
     * @return the requested value or operation outcome
     */
    OperationResult start(ExtensionRegistration extension, int countdownSeconds);
    /**
     * Cancels the identified match and performs standard cleanup.
     * @param context ownership, session, and revision checks for this request
     * @return the requested value or operation outcome
     */
    OperationResult stop(MutationContext context);
    /**
     * Finishes with a custom result, skipping default victory policy but retaining finishing events.
     * @param context ownership, session, and revision checks for this request
     * @param victory candidate result
     * @return the requested value or operation outcome
     */
    OperationResult finish(MutationContext context, Victory victory);
}
