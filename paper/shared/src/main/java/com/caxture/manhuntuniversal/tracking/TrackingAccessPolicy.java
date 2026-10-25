package com.caxture.manhuntuniversal.tracking;

public final class TrackingAccessPolicy {
    private TrackingAccessPolicy() {
    }

    public static boolean mayUse(
            boolean runningParticipant,
            boolean resetPending,
            boolean featureEnabled
    ) {
        return runningParticipant && !resetPending && featureEnabled;
    }

    public static boolean cleanupRequired(
            boolean hasRunningSession,
            boolean participant,
            boolean stateMatches,
            boolean resetPending
    ) {
        return !hasRunningSession || !participant || !stateMatches || resetPending;
    }
}
