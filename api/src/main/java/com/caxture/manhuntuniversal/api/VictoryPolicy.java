package com.caxture.manhuntuniversal.api;


/** Main-thread policy for default victory. Explicit API finish and shutdown bypass this policy. */
@FunctionalInterface
public interface VictoryPolicy {
    /**
     * Evaluates a result before settlement; callback failure conservatively defers it.
     * @param context ownership, session, and revision checks for this request
     * @return the requested value or operation outcome
     */
    VictoryDecision evaluate(VictoryContext context);
}
