package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class PortalSearchEngineTest {
    private final UUID world = UUID.randomUUID();
    private final Queue<Runnable> workers = new ArrayDeque<>();
    private final Map<PortalSearchCursor.Chunk, List<PortalPoint>> columns = new HashMap<>();
    private final List<PortalSearchCursor.Chunk> reads = new ArrayList<>();
    private final List<RuntimeException> failures = new ArrayList<>();
    private long now;
    private long readCost;
    private final PortalSearchEngine engine = new PortalSearchEngine((worldId, x, z) -> {
        assertEquals(world, worldId);
        reads.add(new PortalSearchCursor.Chunk(x, z));
        now += readCost;
        return columns.getOrDefault(new PortalSearchCursor.Chunk(x, z), List.of());
    }, workers::add, () -> now, failures::add, new PortalSearchEngine.Limits(4, 100, 100, 1_000));

    @Test void nearestUsesFullThreeDimensionalDistanceAndAsyncSnapshotThenMainThreadCommit() {
        add(new PortalPoint(3, 100, 0)); // closer horizontally, further in 3D
        PortalPoint nearest = new PortalPoint(17, 64, 0);
        add(nearest);
        List<Optional<PortalPoint>> result = new ArrayList<>();
        engine.request(UUID.randomUUID(), new PortalSearchEngine.Query(world, new PortalPoint(0, 64, 0), 40), result::add);
        engine.tick();
        assertTrue(result.isEmpty());
        assertFalse(workers.isEmpty());
        drain(result);
        assertEquals(List.of(Optional.of(nearest)), result);
        assertTrue(failures.isEmpty());
    }

    @Test void countAndSoftTimeBudgetYieldInsteadOfSearchingTheEntireRadius() {
        List<Optional<PortalPoint>> result = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(40), result::add);
        engine.tick();
        assertEquals(4, reads.size());
        assertTrue(result.isEmpty());
        readCost = 101;
        int previous = reads.size();
        engine.tick();
        assertEquals(previous + 1, reads.size()); // one I/O may exceed the soft budget
        readCost = 0;
        drain(result);
        assertEquals(List.of(Optional.empty()), result);
    }

    @Test void sharedQuerySurvivesFirstSubscriberLeavingAndNearbyQueriesReuseColumnSnapshots() {
        PortalPoint portal = new PortalPoint(1, 64, 1);
        add(portal);
        UUID first = UUID.randomUUID();
        List<Optional<PortalPoint>> result = new ArrayList<>();
        engine.request(first, query(24), ignored -> fail("cancelled owner"));
        engine.request(UUID.randomUUID(), query(24), result::add);
        engine.cancel(first);
        drain(result);
        int previous = reads.size();
        List<Optional<PortalPoint>> nearby = new ArrayList<>();
        engine.request(UUID.randomUUID(), new PortalSearchEngine.Query(world, new PortalPoint(2, 64, 2), 24), nearby::add);
        drain(nearby);
        assertEquals(previous, reads.size());
        assertEquals(Optional.of(portal), nearby.getFirst());
    }

    @Test void missingColumnsAreCachedButPortalChangesInvalidateNegativeSnapshots() {
        List<Optional<PortalPoint>> initial = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(4), initial::add);
        drain(initial);
        int previous = reads.size();
        List<Optional<PortalPoint>> cached = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(4), cached::add);
        assertEquals(List.of(Optional.empty()), cached);
        assertEquals(previous, reads.size());
        PortalPoint portal = new PortalPoint(1, 64, 1);
        add(portal);
        engine.invalidateChunks(world, Set.of(new PortalSearchCursor.Chunk(0, 0)));
        List<Optional<PortalPoint>> changed = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(4), changed::add);
        drain(changed);
        assertEquals(List.of(Optional.of(portal)), changed);
        assertTrue(reads.size() > previous);
    }

    @Test void lateAsyncResultsAfterCancelClearOrWorldUnloadCannotNotifyOrCache() {
        add(new PortalPoint(1, 64, 1));
        UUID player = UUID.randomUUID();
        engine.request(player, query(4), ignored -> fail("cancelled"));
        engine.tick();
        engine.cancel(player);
        workers.forEach(Runnable::run);
        workers.clear();
        engine.tick();
        assertEquals(0, engine.activeCount());
        engine.request(player, query(4), ignored -> fail("unloaded"));
        engine.tick();
        engine.invalidateWorld(world);
        workers.forEach(Runnable::run);
        workers.clear();
        engine.tick();
        assertEquals(0, engine.activeCount());
        engine.request(player, query(4), ignored -> fail("cleared"));
        engine.tick();
        engine.clear();
        workers.forEach(Runnable::run);
        workers.clear();
        engine.tick();
        assertEquals(0, engine.activeCount());
    }

    @Test void failedReadCompletesWithoutCachingFailureOrLeavingSearchStuck() {
        int[] attempts = {0};
        PortalSearchEngine failing = new PortalSearchEngine((id, x, z) -> {
            attempts[0]++;
            throw new IllegalStateException("disk failure");
        }, Runnable::run, () -> 0, failures::add);
        List<Optional<PortalPoint>> result = new ArrayList<>();
        failing.request(UUID.randomUUID(), query(8), result::add);
        failing.tick();
        assertEquals(List.of(Optional.empty()), result);
        assertEquals(0, failing.activeCount());
        failing.request(UUID.randomUUID(), query(8), result::add);
        failing.tick();
        assertEquals(2, attempts[0]);
        assertEquals(2, failures.size());
    }

    @Test void sphericalRadiusAndNegativeCoordinatesMatchBruteForceForDifferentOrigins() {
        var random = new Random(7301);
        List<PortalPoint> points = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            var point = new PortalPoint(random.nextInt(129) - 64, random.nextInt(80) + 20, random.nextInt(129) - 64);
            points.add(point);
            add(point);
        }
        for (int i = 0; i < 12; i++) {
            var origin = new PortalPoint(random.nextInt(61) - 30, 64, random.nextInt(61) - 30);
            int radius = 40;
            var expected = points.stream().filter(point -> origin.distanceSquared(point) <= radius * radius)
                    .min(Comparator.comparingDouble(origin::distanceSquared));
            List<Optional<PortalPoint>> actual = new ArrayList<>();
            engine.request(UUID.randomUUID(), new PortalSearchEngine.Query(world, origin, radius), actual::add);
            drain(actual);
            assertEquals(expected.map(origin::distanceSquared), actual.getFirst().map(origin::distanceSquared));
        }
    }

    @Test void aProvenNearestPortalDoesNotEnumerateTheConfiguredMaximumRadius() {
        PortalPoint atOrigin = new PortalPoint(0, 64, 0);
        add(atOrigin);
        List<Optional<PortalPoint>> result = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(16_384), result::add);
        drain(result);
        assertEquals(List.of(Optional.of(atOrigin)), result);
        assertEquals(1, reads.size());
    }

    @Test void negativeResultExpiresAndRefreshingCannotReuseExpiredColumnSnapshots() {
        List<Optional<PortalPoint>> initial = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(4), initial::add);
        drain(initial);
        int previous = reads.size();
        add(new PortalPoint(1, 64, 1));
        now = 1_000;
        List<Optional<PortalPoint>> refreshed = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(4), refreshed::add);
        drain(refreshed);
        assertEquals(Optional.of(new PortalPoint(1, 64, 1)), refreshed.getFirst());
        assertTrue(reads.size() > previous);
    }

    @Test void columnBudgetIsGlobalAndConsecutiveTicksRotateBetweenIndependentQueries() {
        for (int i = 0; i < 3; i++) {
            engine.request(UUID.randomUUID(), new PortalSearchEngine.Query(world,
                    new PortalPoint(i * 1_000, 64, 0), 80), ignored -> {});
        }
        for (int i = 0; i < 3; i++) {
            int before = reads.size();
            engine.tick();
            assertEquals(4, reads.size() - before);
        }
        for (int i = 0; i < 3; i++) {
            final int center = (i * 1_000) >> 4;
            assertTrue(reads.stream().anyMatch(chunk -> chunk.x() == center && chunk.z() == 0));
        }
    }

    @Test void workerOnlySeesCopiedCoordinatesAndCallbacksStayOnTheTickThread() throws InterruptedException {
        Thread main = Thread.currentThread();
        PortalPoint original = new PortalPoint(1, 64, 1);
        add(original);
        List<Optional<PortalPoint>> result = new ArrayList<>();
        engine.request(UUID.randomUUID(), query(8), value -> {
            assertSame(main, Thread.currentThread());
            result.add(value);
        });
        engine.tick();
        columns.values().forEach(List::clear); // world/reader data changes after snapshot
        while (!workers.isEmpty()) {
            Thread worker = new Thread(workers.remove());
            worker.start();
            worker.join();
        }
        assertTrue(result.isEmpty());
        engine.tick();
        assertEquals(List.of(Optional.of(original)), result);
    }

    @Test void unsupportedRuntimeBindingIsReportedWithoutLeavingTheQueryPending() {
        PortalSearchEngine unsupported = new PortalSearchEngine((id, x, z) -> {
            throw new NoClassDefFoundError("unsupported POI runtime");
        }, Runnable::run, () -> 0, failures::add);
        List<Optional<PortalPoint>> result = new ArrayList<>();
        unsupported.request(UUID.randomUUID(), query(8), result::add);
        unsupported.tick();
        assertEquals(List.of(Optional.empty()), result);
        assertEquals(0, unsupported.activeCount());
        assertEquals(1, failures.size());
    }

    private PortalSearchEngine.Query query(int radius) {
        return new PortalSearchEngine.Query(world, new PortalPoint(0, 64, 0), radius);
    }
    private void add(PortalPoint point) {
        columns.computeIfAbsent(new PortalSearchCursor.Chunk(point.x() >> 4, point.z() >> 4), ignored -> new ArrayList<>()).add(point);
    }
    private void drain(List<?> result) {
        for (int ticks = 0; ticks < 2_000 && result.isEmpty(); ticks++) {
            while (!workers.isEmpty()) workers.remove().run();
            engine.tick();
        }
        assertFalse(result.isEmpty(), "query must finish");
    }
}
