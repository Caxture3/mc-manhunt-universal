package com.caxture.manhuntuniversal.tracking;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DynamicTrackingSelectionTest {
    @Test void dismissSurvivesCountdownUpdatesButOldCatalogChoicesDoNot() {
        assertTrue(DynamicMenuActionPolicy.valid(true,1,2,true));assertFalse(DynamicMenuActionPolicy.valid(true,1,2,false));
        assertFalse(DynamicMenuActionPolicy.valid(false,1,1,true));assertTrue(DynamicMenuActionPolicy.valid(true,1,1,false));
        assertTrue(DynamicMenuActionPolicy.renew(0,241_000_000_000L));assertFalse(DynamicMenuActionPolicy.renew(0,100_000_000_000L));
    }
    @Test void refreshRequestedDuringCooldownIsRetainedUntilDue() {
        var deferred=new DeferredTrackingRefresh();UUID player=UUID.randomUUID();
        deferred.request(player,Instant.EPOCH.plusSeconds(30));
        assertTrue(deferred.drain(Instant.EPOCH.plusSeconds(10)).isEmpty());
        assertEquals(java.util.Set.of(player),deferred.drain(Instant.EPOCH.plusSeconds(30)));assertTrue(deferred.drain(Instant.EPOCH.plusSeconds(31)).isEmpty());
    }
    @Test void movementReferenceUsesProjectedMovementInsteadOfTheLastVisitedSearchPoint() {
        UUID world=UUID.randomUUID();
        var projectedStart=new com.caxture.manhuntuniversal.api.WorldPosition(world,1000,70,0);
        assertFalse(TrackingMovementReference.moved(projectedStart,new com.caxture.manhuntuniversal.api.WorldPosition(world,1400,70,0),512));
        assertTrue(TrackingMovementReference.moved(projectedStart,new com.caxture.manhuntuniversal.api.WorldPosition(world,1600,70,0),512));
    }
}
