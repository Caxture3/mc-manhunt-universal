package com.caxture.manhuntuniversal.tracking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread bounded metadata probes; async reads never generate chunks. */
final class NearbyStructureProbe {
    private static final long TTL = 30_000_000_000L;
    private final Source source;
    private final StructureTargetIndex index;
    private final LongSupplier nanoTime;
    private final Consumer<RuntimeException> failure;
    private final Map<Query, Pending> pending = new LinkedHashMap<>();
    private final Map<Query, Long> checked = new LinkedHashMap<>(16, .75f, true);
    private final Map<WorldChunk, Loading> loading = new LinkedHashMap<>();
    private final Map<WorldChunk, Long> failed = new LinkedHashMap<>();

    NearbyStructureProbe(Source source, StructureTargetIndex index, LongSupplier nanoTime,
                         Consumer<RuntimeException> failure) {
        this.source = source;
        this.index = index;
        this.nanoTime = nanoTime;
        this.failure = failure;
    }

    void request(Query query) {
        request(query, false);
    }

    /** A native locate result may be inspected even when its generated chunk is not resident. */
    void requestTarget(Query query) {
        request(query, true);
    }

    private void request(Query query, boolean loadTarget) {
        Long previous = checked.get(query);
        if (previous != null && nanoTime.getAsLong() - previous < TTL) return;
        if (loadTarget || source.loaded(query.world(), query.chunk())) {
            pending.computeIfAbsent(query, ignored -> new Pending()).loadTarget |= loadTarget;
            while (pending.size() > 128) pending.remove(pending.keySet().iterator().next());
        }
    }

    void tick() {
        for (var entry : List.copyOf(loading.entrySet())) {
            Loading load = entry.getValue();
            if (!load.future.isDone()) continue;
            try {
                if (!load.future.join() && !load.discarded) failed.put(entry.getKey(), nanoTime.getAsLong());
            } catch (RuntimeException exception) {
                if (!load.discarded) {
                    failed.put(entry.getKey(), nanoTime.getAsLong());
                    failure.accept(exception);
                }
            }
            loading.remove(entry.getKey());
        }
        while (failed.size() > 128) failed.remove(failed.keySet().iterator().next());
        int reads = 0;
        for (var entry : List.copyOf(pending.entrySet())) {
            Query query = entry.getKey();
            Pending probe = entry.getValue();
            try {
                if (!source.loaded(query.world(), query.chunk())) {
                    if (!probe.loadTarget) pending.remove(query);
                    else if (failed(query.world(), query.chunk())) complete(query);
                    else ensureLoaded(query.world(), query.chunk());
                    continue;
                }
                if (probe.regions == null) {
                    if (probe.starts == null) {
                        if (reads >= 2) continue;
                        probe.starts = Set.copyOf(source.starts(query.world(), query.chunk(), query.target()));
                        reads++;
                    }
                    if (probe.starts.isEmpty()) { complete(query); continue; }
                    boolean ready = true, unavailable = false;
                    for (var chunk : probe.starts) {
                        if (failed(query.world(), chunk)) { unavailable = true; break; }
                        if (!ensureLoaded(query.world(), chunk)) ready = false;
                    }
                    if (unavailable) { complete(query); continue; }
                    if (!ready || reads >= 2) continue;
                    probe.regions = List.copyOf(source.regions(query.world(), query.chunk(), query.target()));
                    for (Region region : probe.regions) {
                        index.recordRegion(query.world(), query.target(), region.bounds(), region.pieces());
                    }
                    reads++;
                }
                boolean ready = true, hasCandidates = false;
                for (Region region : probe.regions) for (PortalPoint point : region.spawnerCandidates()) {
                    hasCandidates = true;
                    var chunk = new PortalSearchCursor.Chunk(point.x() >> 4, point.z() >> 4);
                    // A failed no-generation load means no verified spawner, not a reason to discard geometry.
                    if (!failed(query.world(), chunk) && !ensureLoaded(query.world(), chunk)) ready = false;
                }
                if (!ready || hasCandidates && reads >= 2) continue;
                for (Region region : probe.regions) {
                    index.recordRegion(query.world(), query.target(), region.bounds(), region.pieces(),
                            source.spawners(query.world(), region.spawnerCandidates()));
                }
                if (hasCandidates) reads++;
                complete(query);
            } catch (RuntimeException | LinkageError exception) {
                complete(query);
                failure.accept(exception instanceof RuntimeException runtime ? runtime
                        : new IllegalStateException("Structure reference runtime is not compatible", exception));
            }
        }
    }

    private boolean failed(UUID world, PortalSearchCursor.Chunk chunk) {
        Long at = failed.get(new WorldChunk(world, chunk));
        return at != null && nanoTime.getAsLong() - at < TTL;
    }

    private boolean ensureLoaded(UUID world, PortalSearchCursor.Chunk chunk) {
        if (source.loaded(world, chunk)) return true;
        WorldChunk key = new WorldChunk(world, chunk);
        if (!loading.containsKey(key) && loading.size() < 2) loading.put(key, new Loading(source.load(world, chunk)));
        return false;
    }

    /** Only known loaded block positions are checked; display-only updates never call this. */
    void validateSpawners() {
        index.pruneSpawners((world, point) -> !source.loaded(world,
                new PortalSearchCursor.Chunk(point.x() >> 4, point.z() >> 4))
                || source.spawners(world, List.of(point)).contains(point));
    }

    private void complete(Query query) {
        pending.remove(query);
        checked.put(query, nanoTime.getAsLong());
        while (checked.size() > 512) checked.remove(checked.keySet().iterator().next());
    }

    void unload(UUID world) {
        pending.keySet().removeIf(query -> query.world().equals(world));
        checked.keySet().removeIf(query -> query.world().equals(world));
        failed.keySet().removeIf(key -> key.world().equals(world));
        for (var entry : List.copyOf(loading.entrySet())) if (entry.getKey().world().equals(world)) {
            entry.getValue().discarded = true;
        }
        index.unload(world);
    }

    void clear() {
        pending.clear(); checked.clear(); failed.clear();
        // Cancelling a derived future cannot cancel Paper's real I/O. Keep permits until it finishes.
        loading.values().forEach(load -> load.discarded = true);
        index.clear();
    }
    int pendingCount() { return pending.size(); }

    record Query(UUID world, PortalSearchCursor.Chunk chunk, TrackingTargetId target) {}
    record Region(StructureTargetIndex.Bounds bounds, List<StructureTargetIndex.Bounds> pieces,
                  List<PortalPoint> spawnerCandidates) {
        Region { pieces = List.copyOf(pieces); spawnerCandidates = List.copyOf(spawnerCandidates); }
        Region(StructureTargetIndex.Bounds bounds, List<StructureTargetIndex.Bounds> pieces) { this(bounds, pieces, List.of()); }
    }
    private record WorldChunk(UUID world, PortalSearchCursor.Chunk chunk) {}
    private static final class Pending { boolean loadTarget; Set<PortalSearchCursor.Chunk> starts; List<Region> regions; }
    private static final class Loading {
        final CompletableFuture<Boolean> future;
        boolean discarded;
        Loading(CompletableFuture<Boolean> future) { this.future = future; }
    }
    interface Source {
        boolean loaded(UUID world, PortalSearchCursor.Chunk chunk);
        Set<PortalSearchCursor.Chunk> starts(UUID world, PortalSearchCursor.Chunk chunk, TrackingTargetId target);
        CompletableFuture<Boolean> load(UUID world, PortalSearchCursor.Chunk chunk);
        List<Region> regions(UUID world, PortalSearchCursor.Chunk chunk, TrackingTargetId target);
        default List<PortalPoint> spawners(UUID world, List<PortalPoint> candidates) { return List.of(); }
    }
}
