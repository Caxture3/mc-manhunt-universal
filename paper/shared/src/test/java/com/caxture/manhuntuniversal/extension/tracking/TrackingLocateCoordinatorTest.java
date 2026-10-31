package com.caxture.manhuntuniversal.extension.tracking;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
class TrackingLocateCoordinatorTest {
    private final UUID session = UUID.randomUUID(), world = UUID.randomUUID();
    private long now;
    private boolean current = true, valid = true;
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:locate"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return valid; } public void close() { valid = false; }
    };
    private final List<TrackingLocateCoordinator.Completion> completed = new ArrayList<>();
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, token -> token == owner && valid, error -> {});
    private final TrackingLocateCoordinator coordinator = new TrackingLocateCoordinator(owned, () -> now,
            request -> current, completed::add);
    private LocateRequest request(UUID player, long version) {
        var point = new WorldPosition(world, 1, 70, 1);
        return new LocateRequest(session, 1, version, player, "test:checkpoint", point, point, Optional.empty(), 4096);
    }
    private TrackingRegistryService.LocatorBinding binding(TrackingLocator locator, boolean share) {
        Optional<Function<LocateRequest, String>> key = share ? Optional.of(ignored -> "shared") : Optional.empty();
        return new TrackingRegistryService.LocatorBinding(locator, locator, Optional.of(owner), false, key, 1);
    }
    @Test void immediateAndBackgroundResultsCommitOnlyWhenCurrentAndWorldMatches() throws Exception {
        UUID player = UUID.randomUUID();
        coordinator.request(request(player, 1), binding((input, cancel) -> CompletableFuture.completedFuture(
                LocateResult.found(new WorldPosition(world, 10, 70, 1), "checkpoint")), false));
        coordinator.tick(); assertEquals(1, completed.size()); assertEquals(LocateResult.Status.FOUND, completed.getFirst().result().status());
        var future = new CompletableFuture<LocateResult>();
        coordinator.request(request(player, 2), binding((input, cancel) -> future, false)); coordinator.tick();
        current = false; Thread worker = new Thread(() -> future.complete(LocateResult.found(new WorldPosition(world, 20, 70, 1), "worker")));
        worker.start(); worker.join(); coordinator.tick(); assertEquals(1, completed.size());
    }
    @Test void eightActualJobsStayBoundedAfterTimeoutAndCancellationUntilUnderlyingCompletion() {
        List<CompletableFuture<LocateResult>> futures = new ArrayList<>();
        TrackingLocator locator = (input, cancel) -> { var future = new CompletableFuture<LocateResult>(); futures.add(future); return future; };
        var binding = binding(locator, false);
        for (int index = 0; index < 10; index++) coordinator.request(request(UUID.randomUUID(), 1), binding);
        coordinator.tick(); assertEquals(8, futures.size()); assertEquals(8, coordinator.inFlight());
        now = 11_000_000_000L; coordinator.tick(); assertEquals(10, completed.size()); assertEquals(8, coordinator.inFlight());
        coordinator.request(request(UUID.randomUUID(), 2), binding); coordinator.tick(); assertEquals(8, futures.size());
        futures.getFirst().complete(LocateResult.notFound("done")); coordinator.tick(); assertEquals(9, futures.size()); assertEquals(8, coordinator.inFlight());
        coordinator.clear(); assertEquals(8, coordinator.inFlight());
    }
    @Test void exactSharedRequestKeepsOtherSubscriberAliveAndRejectsOldOwnerOnCompletion() {
        var future = new CompletableFuture<LocateResult>(); List<CancellationToken> tokens = new ArrayList<>();
        var binding = binding((input, cancel) -> { tokens.add(cancel); return future; }, true);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        coordinator.request(request(first, 1), binding); coordinator.request(request(second, 1), binding);
        coordinator.tick(); assertEquals(1, tokens.size()); coordinator.cancel(first); assertFalse(tokens.getFirst().cancelled());
        future.complete(LocateResult.found(new WorldPosition(world, 10, 70, 1), "shared")); coordinator.tick();
        assertEquals(1, completed.size()); assertEquals(second, completed.getFirst().request().playerId());
        var later = new CompletableFuture<LocateResult>();
        coordinator.request(request(second, 2), binding((input, cancel) -> later, false)); coordinator.tick(); valid = false;
        later.complete(LocateResult.notFound("old plugin")); coordinator.tick(); assertEquals(1, completed.size());
    }
    @Test void differentOriginsAreNotSharedAndReplacingPlayerRequestCancelsOnlyTheOldOne() {
        List<CancellationToken> tokens = new ArrayList<>();
        var binding = binding((input, cancel) -> { tokens.add(cancel); return new CompletableFuture<>(); }, true);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var original = request(first, 1); coordinator.request(original, binding);
        var other = new LocateRequest(session, 1, 1, second, original.targetId(), new WorldPosition(world, 40, 70, 1),
                new WorldPosition(world, 40, 70, 1), Optional.empty(), 4096);
        coordinator.request(other, binding); coordinator.tick(); assertEquals(2, tokens.size());
        coordinator.cancel(first); assertTrue(tokens.getFirst().cancelled()); assertFalse(tokens.getLast().cancelled());
    }
}
