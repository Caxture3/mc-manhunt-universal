package com.caxture.manhuntuniversal.extension.tracking;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class TrackingCatalogTest {
    private final UUID player = UUID.randomUUID(), world = UUID.randomUUID(), session = UUID.randomUUID();
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:tracking"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return true; } public void close() { }
    };
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, token -> token == owner, error -> fail(error));
    private String selected;
    private TeamRole role = TeamRole.RUNNER;
    private boolean eliminated;
    private final TrackingRegistryService.Runtime runtime = new TrackingRegistryService.Runtime() {
        public Optional<TrackingViewerContext> viewer(UUID id) {
            if (!id.equals(player)) return Optional.empty();
            var match = new MatchSnapshot(session, 1, MatchState.RUNNING, Instant.EPOCH, Optional.of(Instant.EPOCH),
                    Map.of(player, new ParticipantSnapshot(player, "Alex", role, eliminated)), Optional.empty());
            return Optional.of(new TrackingViewerContext(player, "en_us", new WorldPosition(world, 0, 70, 0), "overworld", Optional.of(match), Instant.EPOCH));
        }
        public boolean maySelect(UUID id) { return true; }
        public Optional<String> selection(UUID id) { return Optional.ofNullable(selected); }
        public void selection(UUID id, String target) { selected = target; }
        public Optional<TrackingSnapshot> snapshot(UUID id) { return Optional.empty(); }
        public void refresh(UUID id) { }
        public void invalidate(TrackingInvalidation scope) { }
        public void catalogChanged() { }
    };
    private final TrackingRegistryService registry = new TrackingRegistryService(owned, runtime);
    private TrackingTargetDefinition target(String id) {
        return new TrackingTargetDefinition(id, LocalizedText.of(id), TrackingWorld.current(),
                (request, cancellation) -> CompletableFuture.completedFuture(LocateResult.notFound("test")));
    }
    private TrackingMutation mutation(long revision) { return new TrackingMutation(new MutationContext(owner, session, 1, "test"), player, revision); }
    @Test void registerSelectAndUnregisterInvalidateTheTargetAndStaleMenuRevision() {
        var handle = registry.register(owner, target("test:checkpoint")).value().orElseThrow();
        long revision = registry.menu(player).orElseThrow().revision();
        assertTrue(registry.select(mutation(revision), "test:checkpoint").successful()); assertEquals("test:checkpoint", selected);
        registry.register(owner, target("test:other"));
        assertEquals(OperationStatus.STALE_REVISION, registry.select(mutation(revision), "test:checkpoint").status());
        handle.close(); assertNull(selected);
        assertFalse(registry.select(mutation(-1), "test:checkpoint").successful());
    }
    @Test void locksAreCheckedOnServerAndEliminatedOrHunterPlayersCannotSelect() {
        registry.register(owner, target("test:checkpoint"));
        var lock = registry.availability(owner, "test:checkpoint", 1, viewer -> TargetAvailability.locked(5000, "task")).value().orElseThrow();
        assertFalse(registry.select(mutation(-1), "test:checkpoint").successful()); assertNull(selected);
        lock.close(); role = TeamRole.HUNTER;
        assertFalse(registry.select(mutation(-1), "test:checkpoint").successful());
        role = TeamRole.RUNNER; eliminated = true;
        assertFalse(registry.select(mutation(-1), "test:checkpoint").successful());
    }
    @Test void catalogLimitsAndDuplicateIdsAreExplicitAndTimeTicksDoNotEmitRepeatedAvailabilityTransitions() {
        registry.register(owner, target("test:checkpoint"));
        assertEquals(OperationStatus.CONTROL_CONFLICT, registry.register(owner, target("test:checkpoint")).status());
        for (int index = 1; index < 32; index++) assertTrue(registry.register(owner, target("test:t" + index)).successful());
        assertFalse(registry.register(owner, target("test:overflow")).successful());
        long[] remaining = {5000}; List<TrackingChange> changes = new ArrayList<>(); registry.onChange(changes::add);
        registry.availability(owner, "test:checkpoint", 1, viewer -> remaining[0] > 0
                ? TargetAvailability.locked(remaining[0], "wait") : TargetAvailability.available());
        registry.menu(player); changes.clear(); remaining[0] = 4000; registry.menu(player);
        assertTrue(changes.isEmpty()); remaining[0] = 0; registry.menu(player);
        assertEquals(1, changes.stream().filter(change -> change.kind() == TrackingChange.Kind.AVAILABILITY).count());
    }
    @Test void availabilityPolicyCanQueryItsOwnMenuWithoutUnboundedRecursion() {
        registry.register(owner,target("test:checkpoint"));
        registry.availability(owner,"test:checkpoint",1,viewer->{registry.menu(viewer.playerId());return TargetAvailability.available();});
        assertEquals(1,registry.menu(player).orElseThrow().options().size());
    }
}
