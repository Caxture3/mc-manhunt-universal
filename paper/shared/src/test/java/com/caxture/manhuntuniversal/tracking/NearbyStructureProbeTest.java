package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class NearbyStructureProbeTest {
    private final UUID world = UUID.randomUUID();
    private final PortalSearchCursor.Chunk current = new PortalSearchCursor.Chunk(0, 0);
    private final Set<PortalSearchCursor.Chunk> loaded = new HashSet<>(Set.of(current));
    private final Set<PortalSearchCursor.Chunk> references = new LinkedHashSet<>();
    private List<PortalPoint> candidates = List.of();
    private final Set<PortalPoint> validSpawners = new HashSet<>();
    private final Map<PortalSearchCursor.Chunk, CompletableFuture<Boolean>> loads = new LinkedHashMap<>();
    private final StructureTargetIndex index = new StructureTargetIndex(16);
    private long now;
    private int referenceReads, regionReads;
    private final NearbyStructureProbe.Source source = new NearbyStructureProbe.Source() {
        @Override public boolean loaded(UUID id, PortalSearchCursor.Chunk chunk) { return loaded.contains(chunk); }
        @Override public Set<PortalSearchCursor.Chunk> starts(UUID id, PortalSearchCursor.Chunk chunk, TrackingTargetId target) {
            assertTrue(loaded.contains(chunk));
            referenceReads++;
            return Set.copyOf(references);
        }
        @Override public CompletableFuture<Boolean> load(UUID id, PortalSearchCursor.Chunk chunk) {
            var future = new CompletableFuture<Boolean>();
            loads.put(chunk, future);
            return future;
        }
        @Override public List<NearbyStructureProbe.Region> regions(UUID id, PortalSearchCursor.Chunk chunk, TrackingTargetId target) {
            assertTrue(loaded.containsAll(references), "Public structure read must not synchronously resolve missing starts");
            regionReads++;
            var bounds = new StructureTargetIndex.Bounds(0, 60, 0, 64, 80, 16);
            return List.of(new NearbyStructureProbe.Region(bounds, List.of(bounds), candidates));
        }
        @Override public List<PortalPoint> spawners(UUID id, List<PortalPoint> positions) {
            return positions.stream().filter(point -> loaded.contains(new PortalSearchCursor.Chunk(point.x() >> 4, point.z() >> 4)))
                    .filter(validSpawners::contains).toList();
        }
    };
    private final NearbyStructureProbe probe = new NearbyStructureProbe(source, index, () -> now, exception -> fail(exception));
    private final NearbyStructureProbe.Query query = new NearbyStructureProbe.Query(world, current, TrackingTargetId.NETHER_FORTRESS);

    @Test void twoStartLoadsAreGloballyBoundedAndGeometryWaitsForAllOfThem() {
        references.addAll(List.of(new PortalSearchCursor.Chunk(-10, 0), new PortalSearchCursor.Chunk(20, 0),
                new PortalSearchCursor.Chunk(30, 0)));
        probe.request(query);
        probe.request(query); // shared request
        probe.tick();
        assertEquals(2, loads.size());
        assertEquals(0, regionReads);
        for (var entry : List.copyOf(loads.entrySet())) {
            loaded.add(entry.getKey());
            entry.getValue().complete(true);
        }
        probe.tick();
        assertEquals(3, loads.size());
        assertEquals(0, regionReads);
        for (var entry : loads.entrySet()) {
            loaded.add(entry.getKey());
            entry.getValue().complete(true);
        }
        probe.tick();
        assertEquals(1, regionReads);
        assertEquals(1, referenceReads);
        assertEquals(0, probe.pendingCount());
        var viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 8, 70, 8);
        assertEquals(viewer, index.nearest(query.target(), viewer, new PortalPoint(727, 70, 8), 4096).orElseThrow());
    }

    @Test void missingOrUnloadedViewingChunksDoNotCauseTerrainGenerationAndNegativeProbesExpire() {
        var missing = new NearbyStructureProbe.Query(world, new PortalSearchCursor.Chunk(500, 500), query.target());
        probe.request(missing);
        probe.tick();
        assertTrue(loads.isEmpty());
        assertEquals(0, referenceReads);
        probe.request(query);
        probe.tick();
        assertEquals(1, referenceReads);
        assertEquals(0, regionReads);
        probe.request(query);
        probe.tick();
        assertEquals(1, referenceReads);
        now = 31_000_000_000L;
        probe.request(query);
        probe.tick();
        assertEquals(2, referenceReads);
    }

    @Test void unloadingOrClearingBeforeAsyncCompletionCannotInstallGeometry() {
        references.add(new PortalSearchCursor.Chunk(10, 0));
        probe.request(query);
        probe.tick();
        var old = loads.values().iterator().next();
        probe.unload(world);
        old.complete(true);
        probe.tick();
        assertEquals(0, index.size());
        assertEquals(0, probe.pendingCount());
        probe.request(query);
        probe.tick();
        probe.clear();
        for (var future : loads.values()) future.complete(true);
        probe.tick();
        assertEquals(0, index.size());
    }

    @Test void failedNoGenerationLoadFinishesTheProbeInsteadOfLoopingForever() {
        references.add(new PortalSearchCursor.Chunk(10, 0));
        probe.request(query);
        probe.tick();
        loads.values().iterator().next().complete(false);
        probe.tick();
        assertEquals(0, probe.pendingCount());
        assertEquals(0, regionReads);
        probe.request(query);
        probe.tick();
        assertEquals(1, loads.size());
    }

    @Test void clearingAnOldMatchDoesNotReleaseRealIoPermitsUntilItsLoadsActuallyFinish() {
        references.addAll(List.of(new PortalSearchCursor.Chunk(10, 0), new PortalSearchCursor.Chunk(20, 0)));
        probe.request(query);
        probe.tick();
        var oldLoads = List.copyOf(loads.values());
        assertEquals(2, oldLoads.size());
        probe.clear();
        references.clear();
        references.addAll(List.of(new PortalSearchCursor.Chunk(30, 0), new PortalSearchCursor.Chunk(40, 0)));
        probe.request(query);
        probe.tick();
        assertEquals(2, loads.size(), "Cancelled subscribers must not free still-running native I/O slots");
        for (var future : oldLoads) future.complete(false);
        probe.tick();
        assertEquals(4, loads.size());
        assertEquals(0, regionReads);
    }

    @Test void candidatesShareTheAsyncBudgetAndOnlyActualBlazeSpawnersBecomeTargets() {
        var near = new PortalPoint(12, 70, 8);
        var far = new PortalPoint(40, 70, 8);
        candidates = List.of(far, near);
        validSpawners.addAll(candidates);
        references.add(current);
        probe.request(query);
        probe.tick();
        assertEquals(1, loads.size());
        var viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 8, 70, 8);
        assertEquals(viewer, index.nearest(query.target(), viewer, null, 4096).orElseThrow(),
                "An unverified candidate cannot override the structure fallback");
        loaded.add(new PortalSearchCursor.Chunk(2, 0));
        loads.values().iterator().next().complete(true);
        probe.tick();
        assertEquals(12, index.nearest(query.target(), viewer, null, 4096).orElseThrow().x());
        validSpawners.remove(near);
        probe.validateSpawners();
        assertEquals(40, index.nearest(query.target(), viewer, null, 4096).orElseThrow().x());
        validSpawners.clear();
        probe.validateSpawners();
        assertEquals(viewer, index.nearest(query.target(), viewer, null, 4096).orElseThrow());
    }

    @Test void aMissingCandidateChunkFallsBackWithoutGeneratingOrInventingASpawner() {
        candidates = List.of(new PortalPoint(40, 70, 8));
        references.add(current);
        probe.request(query);
        probe.tick();
        loads.values().iterator().next().complete(false);
        probe.tick();
        assertEquals(0, probe.pendingCount());
        var viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 8, 70, 8);
        assertEquals(viewer, index.nearest(query.target(), viewer, null, 4096).orElseThrow());
    }

    @Test void aLocatedFortressMayProbeItsGeneratedTargetChunkWithinTheSameIoBudget() {
        loaded.clear();
        references.add(current);
        candidates = List.of(new PortalPoint(12, 70, 8));
        validSpawners.addAll(candidates);
        probe.requestTarget(query);
        probe.tick();
        assertEquals(1, loads.size());
        assertEquals(0, referenceReads);
        loaded.add(current);
        loads.get(current).complete(true);
        probe.tick();
        probe.tick();
        var viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 8, 70, 8);
        assertEquals(12, index.nearest(query.target(), viewer, null, 4096).orElseThrow().x());
        assertEquals(0, probe.pendingCount());
    }

    @Test void aMissingLocatedTargetChunkKeepsTheNativeFallbackAndDoesNotRetryImmediately() {
        loaded.clear();
        var nativePoint = new PortalPoint(8, 70, 8);
        index.recordPoint(world, query.target(), nativePoint);
        probe.requestTarget(query);
        probe.tick();
        loads.get(current).complete(false);
        probe.tick();
        probe.requestTarget(query);
        probe.tick();
        assertEquals(0, probe.pendingCount());
        assertEquals(0, referenceReads);
        assertEquals(1, loads.size());
        var viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 100, 70, 8);
        assertEquals(8, index.nearest(query.target(), viewer, nativePoint, 4096).orElseThrow().x());
    }
}
