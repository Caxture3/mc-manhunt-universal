package com.caxture.manhuntuniversal.api;
import com.caxture.manhuntuniversal.api.tracking.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Api12CompatibilityTest {
    @Test void original11TrackingImplementationInheritsOptionalControlWithoutLinkageErrors()throws Exception{
        var legacy=Class.forName("compatibility.LegacyTrackingConsumer");assertEquals(true,legacy.getMethod("probe").invoke(null));
        var registry=(TrackingRegistry)legacy.getConstructor().newInstance();
        assertTrue(TrackingRegistry.class.getMethod("control").isDefault());assertThrows(UnsupportedOperationException.class,registry::control);
    }
    @Test void overrideSourcesAndExactRevisionsAreValidated(){
        assertThrows(IllegalArgumentException.class,()->new TrackingOverrideRequest(TrackingOverrideMode.TARGET,Optional.empty(),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new TrackingOverrideRequest(TrackingOverrideMode.DISABLED,Optional.of("core:village"),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new TrackingControlMutation(new TrackingMutation(new MutationContext(new ExtensionRegistration(){
            public String id(){return "test";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}
        },UUID.randomUUID(),"missing exact revision"),UUID.randomUUID()),0));
    }
}
