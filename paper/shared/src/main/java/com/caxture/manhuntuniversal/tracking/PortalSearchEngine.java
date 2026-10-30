package com.caxture.manhuntuniversal.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread coordinator; workers receive immutable block coordinates only. */
final class PortalSearchEngine {
    private static final int MAX_CACHED_POINTS = 262_144;
    private static final int COLUMNS_PER_TURN = 8;
    private final ChunkReader reader;
    private final Executor executor;
    private final LongSupplier nanoTime;
    private final Consumer<RuntimeException> onFailure;
    private final Limits limits;
    private final Map<Column, Snapshot> snapshots = new LinkedHashMap<>(16, .75f, true);
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final SharedSearchCache<Query, PortalPoint> results;
    private int cachedPoints;
    private int firstJob;

    PortalSearchEngine(ChunkReader reader, Executor executor, LongSupplier nanoTime,
                       Consumer<RuntimeException> onFailure) {
        this(reader, executor, nanoTime, onFailure,
                new Limits(64, 2_000_000, 32_768, 30_000_000_000L));
    }

    PortalSearchEngine(ChunkReader reader, Executor executor, LongSupplier nanoTime,
                       Consumer<RuntimeException> onFailure, Limits limits) {
        this.reader = reader;
        this.executor = executor;
        this.nanoTime = nanoTime;
        this.onFailure = onFailure;
        this.limits = limits;
        results = new SharedSearchCache<>(512, limits.snapshotTtlNanos(), limits.snapshotTtlNanos(),
                nanoTime, this::cancelWork);
    }

    void request(UUID subscriber, Query query, Consumer<Optional<PortalPoint>> callback) {
        var work = results.request(subscriber, query, callback);
        if (work != null) jobs.put(work.id(), new Job(work));
    }

    void cancel(UUID subscriber) { results.cancel(subscriber); }

    /** Soft global budget: a single synchronous column read cannot be interrupted. */
    void tick() {
        for (Job job : List.copyOf(jobs.values())) {
            try {
                if (job.pending != null && job.pending.isDone()) {
                    var candidate = job.pending.join(); // isDone: never waits on a worker
                    job.pending = null;
                    candidate.ifPresent(point -> job.accept(point));
                }
                if (job.pending == null) completeIfProven(job);
            } catch (RuntimeException | LinkageError exception) { fail(job, exception); }
        }
        List<Job> turn = List.copyOf(jobs.values());
        if (turn.isEmpty()) return;
        long started = nanoTime.getAsLong();
        int processed = 0;
        int offset = Math.floorMod(firstJob++, turn.size());
        boolean progress = true;
        while (progress && processed < limits.maxColumnsPerTick()
                && nanoTime.getAsLong() - started < limits.maxNanosPerTick()) {
            progress = false;
            for (int i = 0; i < turn.size(); i++) {
                Job job = turn.get((offset + i) % turn.size());
                if (!job.work.active() || job.pending != null) continue;
                List<PortalPoint> batch = new ArrayList<>();
                try {
                    for (int count = 0; count < COLUMNS_PER_TURN
                            && processed < limits.maxColumnsPerTick()
                            && nanoTime.getAsLong() - started < limits.maxNanosPerTick(); count++) {
                        if (completeIfProven(job)) break;
                        var chunk = job.cursor.next();
                        if (chunk == null) {
                            // No async batch may be discarded by completion here.
                            break;
                        }
                        processed++;
                        progress = true;
                        batch.addAll(snapshot(job.work.key().worldId(), chunk));
                        // Await this ring's candidates before advancing far beyond its boundary.
                        if (!batch.isEmpty()) break;
                    }
                    if (!batch.isEmpty()) {
                        List<PortalPoint> immutable = List.copyOf(batch);
                        Query query = job.work.key();
                        job.pending = CompletableFuture.supplyAsync(() -> nearest(query, immutable), executor);
                    } else completeIfProven(job);
                } catch (RuntimeException | LinkageError exception) { fail(job, exception); }
            }
        }
    }

    private boolean completeIfProven(Job job) {
        if (!job.work.active()) return true;
        double remaining = job.cursor.minimumRemainingDistanceSquared();
        double radiusSquared = (double) job.work.key().radius() * job.work.key().radius();
        if (remaining > radiusSquared || job.best != null && job.bestDistance <= remaining) {
            job.work.complete(Optional.ofNullable(job.best));
            return true;
        }
        return false;
    }

    private List<PortalPoint> snapshot(UUID world, PortalSearchCursor.Chunk chunk) {
        Column key = new Column(world, chunk);
        Snapshot found = snapshots.get(key);
        long now = nanoTime.getAsLong();
        if (found != null && now - found.createdAt < limits.snapshotTtlNanos()) return found.points;
        removeSnapshot(key);
        List<PortalPoint> points = List.copyOf(reader.read(world, chunk.x(), chunk.z()));
        if (points.size() <= MAX_CACHED_POINTS) {
            snapshots.put(key, new Snapshot(points, nanoTime.getAsLong()));
            cachedPoints += points.size();
            while (snapshots.size() > limits.chunkCacheCapacity() || cachedPoints > MAX_CACHED_POINTS) {
                removeSnapshot(snapshots.keySet().iterator().next());
            }
        }
        return points;
    }

    private void removeSnapshot(Column column) {
        Snapshot removed = snapshots.remove(column);
        if (removed != null) cachedPoints -= removed.points.size();
    }

    void invalidateChunks(UUID world, Set<PortalSearchCursor.Chunk> chunks) {
        chunks.forEach(chunk -> removeSnapshot(new Column(world, chunk)));
        // A new portal could be closer to any origin: invalidate whole-query results/work.
        results.invalidateMatching(query -> query.worldId().equals(world));
    }

    void invalidateWorld(UUID world) {
        for (Column column : new ArrayList<>(snapshots.keySet())) {
            if (column.world.equals(world)) removeSnapshot(column);
        }
        results.invalidateMatching(query -> query.worldId().equals(world));
    }

    void clear() {
        results.clear();
        snapshots.clear();
        cachedPoints = 0;
        firstJob = 0;
    }

    int activeCount() { return jobs.size(); }

    private void cancelWork(UUID id) {
        Job removed = jobs.remove(id);
        if (removed != null && removed.pending != null) removed.pending.cancel(false);
    }

    private void fail(Job job, Throwable exception) {
        try { job.work.fail(); }
        finally { onFailure.accept(exception instanceof RuntimeException runtime ? runtime
                : new IllegalStateException("Portal POI runtime is not compatible", exception)); }
    }

    private static Optional<PortalPoint> nearest(Query query, List<PortalPoint> immutable) {
        PortalPoint best = null;
        double distance = Double.POSITIVE_INFINITY;
        double radiusSquared = (double) query.radius() * query.radius();
        for (PortalPoint point : immutable) {
            double candidate = query.origin().distanceSquared(point);
            if (candidate <= radiusSquared && candidate < distance) {
                best = point;
                distance = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    record Query(UUID worldId, PortalPoint origin, int radius) {
        Query {
            Objects.requireNonNull(worldId);
            Objects.requireNonNull(origin);
            if (radius <= 0) throw new IllegalArgumentException("radius must be positive");
        }
    }
    record Limits(int maxColumnsPerTick, long maxNanosPerTick, int chunkCacheCapacity, long snapshotTtlNanos) {
        Limits {
            if (maxColumnsPerTick < 1 || maxNanosPerTick < 1 || chunkCacheCapacity < 1 || snapshotTtlNanos < 1)
                throw new IllegalArgumentException();
        }
    }
    @FunctionalInterface interface ChunkReader { List<PortalPoint> read(UUID world, int chunkX, int chunkZ); }
    private record Column(UUID world, PortalSearchCursor.Chunk chunk) {}
    private record Snapshot(List<PortalPoint> points, long createdAt) {}

    private final class Job {
        private final SharedSearchCache<Query, PortalPoint>.Work work;
        private final PortalSearchCursor cursor;
        private CompletableFuture<Optional<PortalPoint>> pending;
        private PortalPoint best;
        private double bestDistance = Double.POSITIVE_INFINITY;
        private Job(SharedSearchCache<Query, PortalPoint>.Work work) {
            this.work = work;
            cursor = new PortalSearchCursor(work.key().origin(), work.key().radius());
        }
        private void accept(PortalPoint point) {
            double distance = work.key().origin().distanceSquared(point);
            if (distance < bestDistance) {
                best = point;
                bestDistance = distance;
            }
        }
    }
}
