package com.caxture.manhuntuniversal.tracking;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TrackingLocateQueryTest {
    @Test void projectionInvalidationDropsResultAndOldGenerationWithoutSkippingCooldown(){
        var actual=new WorldPosition(UUID.randomUUID(),128,60,-32);var origin=new WorldPosition(UUID.randomUUID(),16,80,-4);
        var request=new LocateRequest(UUID.randomUUID(),3,7,UUID.randomUUID(),"test:structure",actual,origin,Optional.empty(),4096);
        var query=new TrackingLocateQuery(request,Instant.EPOCH,origin);query.pending=false;query.result=LocateResult.found(origin,"old");
        var invalid=query.invalidated(8);assertNull(invalid.result);assertFalse(invalid.pending);assertTrue(invalid.refresh);
        assertFalse(invalid.current(request));assertTrue(invalid.current(invalid.request));assertEquals(Instant.EPOCH,invalid.submittedAt);
        assertFalse(invalid.ready(Instant.EPOCH.plusSeconds(29),30,false,true));assertTrue(invalid.ready(Instant.EPOCH.plusSeconds(30),30,false,true));
        assertEquals(request.sessionId(),invalid.request.sessionId());assertEquals(request.origin(),invalid.request.origin());assertEquals(request.targetRevision(),invalid.request.targetRevision());
    }
    @Test void pendingAndUnmovedQueriesRetainExistingSchedulingRules(){
        var point=new WorldPosition(UUID.randomUUID(),0,70,0);
        var query=new TrackingLocateQuery(new LocateRequest(UUID.randomUUID(),1,1,UUID.randomUUID(),"test:target",point,point,Optional.empty(),4096),Instant.EPOCH,point);
        assertFalse(query.ready(Instant.EPOCH.plusSeconds(100),30,true,false));query.pending=false;
        assertFalse(query.ready(Instant.EPOCH.plusSeconds(100),30,false,true));assertTrue(query.ready(Instant.EPOCH.plusSeconds(100),30,true,true));
    }
}
