package com.caxture.manhuntuniversal.client;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DynamicTrackingRoutingTest {
    @Test void anOverlongApiReasonFallsBackToDialogInsteadOfEscapingTheMenuLoop() {
        var menu=new TrackingMenuSnapshot(1,Optional.empty(),List.of(new TrackingOptionSnapshot("test:target","target","","",1,
                TrackingAppearance.generic(),TargetAvailability.locked(0,"x".repeat(129)))));
        assertTrue(DynamicTrackingMessages.tryOpen(UUID.randomUUID(),1,menu).isEmpty());
    }
    @Test void onlyV3HandshakeEnablesDynamicCatalogAndDescriptorsKeepExtensionIds() {
        UUID player = UUID.randomUUID(); var capabilities = new ClientCapabilityRegistry();
        assertFalse(capabilities.accept(player, new ClientHello(2))); assertFalse(capabilities.supports(player));
        assertTrue(capabilities.accept(player, new ClientHello(3)));
        var menu = new TrackingMenuSnapshot(9, Optional.of("extension:checkpoint"), List.of(new TrackingOptionSnapshot(
                "extension:checkpoint", "检查点", "CHECKPOINT", "说明", 1, TrackingAppearance.generic(), TargetAvailability.available())));
        var message = DynamicTrackingMessages.open(UUID.randomUUID(), 5, menu);
        assertEquals("extension:checkpoint", message.targets().getFirst().id());
        assertEquals("检查点", message.targets().getFirst().name());
        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)));
        capabilities.accept(player, new ClientHello(2)); assertFalse(capabilities.supports(player));
    }
}
