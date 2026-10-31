package com.caxture.manhuntuniversal.extension.config;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRegistryTest {
    @Test void frozenCoreSettingsCanBeRestoredWithoutChangingTheirSavedNextMatchValues() {
        var base=definition("core-next",ConfigTypes.integers(0,10),1);
        registry.registerCore(new ConfigDefinition<>(base.key(),base.name(),base.description(),base.defaultValue(),base.type(),base.storage(),ConfigTiming.NEXT_MATCH,""));
        UUID match=UUID.randomUUID();registry.restoreMatch(match,Map.of("core-next",7));
        var value=registry.find("core-next").orElseThrow();assertEquals(7,value.effectiveValue());assertEquals(1,value.savedValue());assertEquals(1,value.nextMatchValue());
        assertEquals(Map.of("core-next",7),registry.frozenCoreSnapshot());
        assertThrows(IllegalArgumentException.class,()->registry.restoreMatch(match,Map.of("core-next",99)));
        assertEquals(7,registry.find("core-next").orElseThrow().effectiveValue());
        registry.endMatch(match);assertEquals(1,registry.find("core-next").orElseThrow().effectiveValue());
    }
    private boolean ownerAlive=true;
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:config"; }
        public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return ownerAlive; }
        public void close() { }
    };
    private final List<Set<String>> effects = new ArrayList<>();
    private final List<RuntimeException> errors = new ArrayList<>();
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, value -> value == owner && ownerAlive, errors::add);
    private final ConfigurationRegistryService registry = new ConfigurationRegistryService(owned, keys -> effects.add(keys));
    private Object stored = 1;
    private boolean save = true;
    private void scalar() {
        assertTrue(registry.register(owner, definition("test:count", ConfigTypes.integers(0, 10), 1)).successful());
    }
    private <T> ConfigDefinition<T> definition(String id, ConfigType<T> type, T fallback) {
        return new ConfigDefinition<>(id, LocalizedText.of(id), LocalizedText.of("description"), fallback,
                type, new ConfigStorage<>() {
                    @SuppressWarnings("unchecked") public T read() { return (T) stored; }
                    public boolean save(T value) { if (!save) return false; stored = value; return true; }
                }, ConfigTiming.IMMEDIATE, "");
    }
    @Test void savedChangesRemainUnderOverridesAndRevealTheCurrentValueAfterClose() {
        scalar();
        var overlay = registry.override(owner, "test:count", ignored -> 9, 100).value().orElseThrow();
        assertEquals(9, registry.find("test:count").orElseThrow().effectiveValue());
        assertTrue(registry.update(owner, "test:count", 3, -1).successful());
        var snapshot = registry.find("test:count").orElseThrow();
        assertEquals(3, snapshot.savedValue()); assertEquals(9, snapshot.effectiveValue());
        overlay.close();
        assertEquals(3, registry.find("test:count").orElseThrow().effectiveValue());
    }
    @Test void failedPersistenceAndStaleRevisionCannotChangeEffectiveValueOrEmitEffects() {
        scalar(); effects.clear(); save = false;
        assertFalse(registry.update(owner, "test:count", 4, -1).successful());
        assertEquals(1, registry.find("test:count").orElseThrow().effectiveValue());
        assertTrue(effects.isEmpty());
        save = true; long revision = registry.find("test:count").orElseThrow().revision();
        registry.update(owner, "test:count", 2, revision);
        assertEquals(OperationStatus.STALE_REVISION, registry.update(owner, "test:count", 4, revision).status());
    }
    @Test void groupValidationAndParentLocksAreAtomicButLeafLocksLeaveSiblingsWritable() {
        stored = Map.of("a", 1, "b", 2);
        var type = ConfigTypes.group(Map.of("a", new ConfigField<>(LocalizedText.of("A"), ConfigTypes.integers(-1, 10), "s"),
                "b", new ConfigField<>(LocalizedText.of("B"), ConfigTypes.integers(-1, 10), "s")));
        registry.register(owner, definition("test:times", type, Map.of("a", 1, "b", 2)));
        assertFalse(registry.update(owner, "test:times", Map.of("a", 3, "b", 99), -1).successful());
        assertEquals(Map.of("a", 1, "b", 2), stored);
        var leafLock = registry.lock(owner, "test:times.a", 1, "managed").value().orElseThrow();
        assertFalse(registry.update(owner, "test:times.a", 3, -1).successful());
        assertTrue(registry.update(owner, "test:times.b", 4, -1).successful());
        assertFalse(registry.update(owner, "test:times", Map.of("a", 1, "b", 3), -1).successful());
        leafLock.close();
        var parent = registry.lock(owner, "test:times", 2, "all").value().orElseThrow();
        assertFalse(registry.update(owner, "test:times.b", 5, -1).successful());
        parent.close(); assertTrue(registry.update(owner, "test:times.a", -1, -1).successful());
    }
    @Test void equalPriorityOverlapsConflictAndObserverFailureDoesNotUndoACommit() {
        scalar();
        var layer = registry.override(owner, "test:count", ignored -> 8, 1).value().orElseThrow();
        assertEquals(OperationStatus.CONTROL_CONFLICT, registry.override(owner, "test:count", ignored -> 9, 1).status());
        layer.close();
        registry.onChange(keys -> { throw new IllegalStateException("observer"); });
        assertTrue(registry.update(owner, "test:count", 2, -1).successful());
        assertEquals(2, stored); assertFalse(errors.isEmpty());
    }
    @Test void nextMatchValuesStayDistinctAndSubscribersObserveOnlySuccessfulChanges() {
        var base = definition("test:next", ConfigTypes.integers(0,10),1);
        registry.register(owner,new ConfigDefinition<>(base.key(),base.name(),base.description(),base.defaultValue(),base.type(),base.storage(),ConfigTiming.NEXT_MATCH,""));
        List<ConfigurationChange> changes = new ArrayList<>(); registry.listen(owner,changes::add);
        UUID match=UUID.randomUUID(); registry.beginMatch(match);
        registry.update(owner,"test:next",4,-1);
        var value=registry.find("test:next").orElseThrow(); assertEquals(1,value.effectiveValue()); assertEquals(4,value.nextMatchValue());
        save=false; int count=changes.size(); registry.update(owner,"test:next",5,-1); assertEquals(count,changes.size());
        registry.endMatch(match); assertEquals(4,registry.find("test:next").orElseThrow().effectiveValue());
    }
    @Test void transformingOverrideRefreshDoesNotFeedItsOwnPreviousValueBackIntoItself() {
        scalar();registry.override(owner,"test:count",value->((Number)value.effectiveValue()).intValue()+1,10);
        assertEquals(2,registry.find("test:count").orElseThrow().effectiveValue());
        registry.refresh(owner,"test:count");assertEquals(2,registry.find("test:count").orElseThrow().effectiveValue());
    }
    @Test void closingAnOwnerNeverActivatesItsLowerPriorityLayerDuringCleanup() {
        stored=false;registry.register(owner,definition("test:forced",ConfigTypes.booleans(),false));
        registry.override(owner,"test:forced",ignored->true,1);
        var high=registry.override(owner,"test:forced",ignored->false,2).value().orElseThrow();
        effects.clear();ownerAlive=false;high.close();
        assertEquals(false,registry.find("test:forced").orElseThrow().effectiveValue());
        assertTrue(effects.isEmpty());
    }
}
