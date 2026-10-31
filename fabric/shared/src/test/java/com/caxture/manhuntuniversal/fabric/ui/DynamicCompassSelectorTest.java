package com.caxture.manhuntuniversal.fabric.ui;
import com.caxture.manhuntuniversal.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DynamicCompassSelectorTest {
    private TrackingTargetDescriptor option(String id, long time) { return new TrackingTargetDescriptor(id, id, "", "", 1, "generic", 0,
            Optional.empty(), Optional.empty(), time, time == 0, ""); }
    @Test void pagesRemainEqualWidthOrderedAndClampAfterCatalogRemoval() {
        var state = new SelectorPageState(1, Optional.empty(), List.of(option("test:d",0),option("test:c",0),option("test:b",0),option("test:a",0)),0);
        assertEquals(2, state.pages()); assertEquals("test:a", state.options().getFirst().id());
        var canvas = new SelectorRect(0,0,100,60);
        assertEquals(100, state.panel(canvas,0).width() + state.panel(canvas,1).width() + state.panel(canvas,2).width());
        state.next(); assertEquals(1, state.options().size()); assertEquals(100,state.panel(canvas,0).width());
        state.update(2, Optional.empty(), List.of(option("test:a",0)), 1);
        assertEquals(0,state.page()); assertEquals(1,state.pages()); assertTrue(state.hit(canvas,101,0).isEmpty());
        state.update(1,Optional.empty(),List.of(option("test:stale",0)),2); assertEquals("test:a",state.options().getFirst().id());
    }
    @Test void taskLocksStayLockedAndTimedLocksUseMonotonicElapsedTime() {
        var timed = option("test:timed",1500);
        var task = new TrackingTargetDescriptor("test:task","task","","",0,"generic",0,Optional.empty(),Optional.empty(),0,false,"task");
        var state = new SelectorPageState(0,Optional.empty(),List.of(timed,task),0);
        assertFalse(state.enabled(timed,1_000_000_000L)); assertTrue(state.enabled(timed,2_000_000_000L));
        assertFalse(state.enabled(task,2_000_000_000L)); assertEquals(0,state.remaining(timed,2_000_000_000L));
    }
}
