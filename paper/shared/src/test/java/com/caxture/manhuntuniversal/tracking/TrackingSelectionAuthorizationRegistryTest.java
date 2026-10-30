package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrackingSelectionAuthorizationRegistryTest {
    @Test void staleCloseOrChoiceCannotConsumeAReplacementMenuAndInspectionDoesNotConsume() {
        var registry = new TrackingSelectionAuthorizationRegistry();
        UUID player = UUID.randomUUID(), session = UUID.randomUUID();
        var old = registry.issue(player, session);
        var replacement = registry.issue(player, session);
        assertFalse(registry.consume(player, session, old.authorizationId()));
        assertEquals(replacement, registry.current(player).orElseThrow());
        assertFalse(registry.consume(player, UUID.randomUUID(), replacement.authorizationId()));
        assertEquals(replacement, registry.current(player).orElseThrow());
        assertTrue(registry.consume(player, session, replacement.authorizationId()));
        assertTrue(registry.current(player).isEmpty());
        assertFalse(registry.consume(player, session, replacement.authorizationId()));
    }
}
