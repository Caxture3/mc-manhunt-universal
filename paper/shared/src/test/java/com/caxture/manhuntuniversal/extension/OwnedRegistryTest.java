package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OwnedRegistryTest {
    private boolean main = true;
    private OperationStatus gate;
    private final Set<ExtensionRegistration> valid = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<RuntimeException> errors = new ArrayList<>();
    private final OwnedRegistry owned = new OwnedRegistry(() -> {
        if (!main) throw new IllegalStateException("wrong thread");
    }, () -> main ? gate : OperationStatus.WRONG_THREAD, valid::contains, errors::add);
    private ExtensionRegistration owner() {
        var token = new ExtensionRegistration() {
            public String id() { return "test:owner"; }
            public Set<ControlDomain> controls() { return Set.of(); }
            public boolean active() { return valid.contains(this); }
            public void close() { valid.remove(this); owned.closeOwner(this); }
        };
        valid.add(token);
        return token;
    }
    @Test void independentChildrenCloseOnceAndParentCloseReleasesTheRemainingOnes() {
        var owner = owner(); var count = new AtomicInteger();
        var first = owned.execute(owner, () -> ExtensionResult.ok(owned.add(owner, count::incrementAndGet))).value().orElseThrow();
        var second = owned.execute(owner, () -> ExtensionResult.ok(owned.add(owner, count::incrementAndGet))).value().orElseThrow();
        first.close(); first.close();
        assertEquals(1, count.get()); assertTrue(second.active());
        owner.close();
        assertEquals(2, count.get()); assertFalse(second.active());
        owned.closeOwner(owner); assertEquals(2, count.get());
    }
    @Test void invalidOwnersWrongThreadAndExternalReentryAreRejectedWithoutRunningAction() {
        var owner = owner(); valid.remove(owner);
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION, owned.execute(owner, () -> fail()).status());
        valid.add(owner); main = false;
        assertEquals(OperationStatus.WRONG_THREAD, owned.execute(owner, () -> fail()).status());
        main = true; gate = OperationStatus.BUSY;
        assertEquals(OperationStatus.BUSY, owned.execute(owner, () -> fail()).status());
    }
    @Test void callbacksCannotMutateAndThrowingCleanupDoesNotStopOtherResources() {
        var owner = owner();
        var nested = owned.execute(owner, () -> owned.execute(owner, () -> fail()));
        assertEquals(OperationStatus.BUSY, nested.status());
        owned.execute(owner, () -> ExtensionResult.ok(owned.add(owner, () -> { throw new IllegalStateException("bad"); })));
        var count = new AtomicInteger();
        var child = owned.execute(owner, () -> ExtensionResult.ok(owned.add(owner, count::incrementAndGet))).value().orElseThrow();
        owned.closeOwner(owner);
        assertEquals(1, count.get()); assertFalse(child.active()); assertEquals(1, errors.size());
    }
}
