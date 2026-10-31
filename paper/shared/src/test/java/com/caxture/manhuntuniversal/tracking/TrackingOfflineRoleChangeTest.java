package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackingOfflineRoleChangeTest {
    @Test void rebuildRetainsOfflineWorldHistoryAndClearsTheOldStructureSelection() {
        UUID session=UUID.randomUUID(),player=UUID.randomUUID(),overworld=UUID.randomUUID(),nether=UUID.randomUUID();
        var previous=new TrackingSessionState(session);
        var first=new TrackingPosition(overworld,TrackingWorldKind.OVERWORLD,10,70,20);
        var latest=new TrackingPosition(nether,TrackingWorldKind.NETHER,2,65,3);
        previous.recordPosition(player,first);previous.recordPosition(player,latest);previous.setRunnerSelection(player,TrackingTargetId.VILLAGE);
        var rebuilt=new TrackingSessionState(session,previous.positionSnapshot());
        assertEquals(Optional.of(latest),rebuilt.latestPosition(player));assertEquals(Optional.of(first),rebuilt.lastPosition(player,overworld));
        assertEquals(Optional.of(nether),rebuilt.recentWorld(player,TrackingWorldKind.NETHER));assertEquals(TrackingTargetId.NONE,rebuilt.runnerSelection(player));
        var candidate=new TrackingPlayerCandidate(player,"Offline",false,rebuilt.latestPosition(player),rebuilt.lastPosition(player,overworld));
        var target=TrackingPolicy.nearestRunner(new TrackingPosition(overworld,TrackingWorldKind.OVERWORLD,0,70,0),List.of(candidate)).orElseThrow();
        assertEquals(first,target.direction());assertFalse(target.liveSameWorld());
    }
}
