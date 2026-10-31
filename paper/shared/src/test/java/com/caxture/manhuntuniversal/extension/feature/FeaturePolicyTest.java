package com.caxture.manhuntuniversal.extension.feature;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.feature.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeaturePolicyTest {
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:policy"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return true; } public void close() { }
    };
    private final List<RuntimeException> errors = new ArrayList<>();
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, token -> token == owner, errors::add);
    private final FeatureRegistryService registry = new FeatureRegistryService(owned);
    private FeatureContext environment(String id) {
        return new FeatureContext(id, "environment", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }
    @Test void everyCoreFeatureCanBeSuppressedIndependentlyAndRestored() {
        assertEquals(9, registry.list().size());
        for (String id : registry.list()) {
            var handle = registry.policy(owner, id, 5, context -> FeatureDecision.SKIP_CORE).value().orElseThrow();
            assertFalse(registry.allows(environment(id)));
            for (String other : registry.list()) if (!other.equals(id)) assertTrue(registry.allows(environment(other)));
            handle.close(); assertTrue(registry.allows(environment(id)));
        }
    }
    @Test void highestPriorityWinsAndEqualReplacementConflicts() {
        String id = com.caxture.manhuntuniversal.api.feature.CoreFeatureIds.BLAZE_ROD_LOOTING;
        registry.policy(owner, id, 1, context -> FeatureDecision.SKIP_CORE);
        assertEquals(OperationStatus.CONTROL_CONFLICT, registry.policy(owner, id, 1, context -> FeatureDecision.DEFAULT).status());
        var highest = registry.policy(owner, id, 2, context -> FeatureDecision.DEFAULT).value().orElseThrow();
        assertTrue(registry.allows(environment(id)));
        highest.close(); assertFalse(registry.allows(environment(id)));
    }
    @Test void exceptionFallsBackToCoreAndNestedWritesAreRejected() {
        String id = CoreFeatureIds.NETHER_PORTAL_PROTECTION;
        registry.policy(owner, id, 1, context -> {
            assertTrue(context.playerId().isEmpty());
            assertEquals(OperationStatus.BUSY, registry.policy(owner, id, 3, ignored -> FeatureDecision.DEFAULT).status());
            throw new IllegalStateException("extension failed");
        });
        assertTrue(registry.allows(environment(id))); assertEquals(1, errors.size());
        owned.closeOwner(owner); assertTrue(registry.allows(environment(id)));
    }
}
