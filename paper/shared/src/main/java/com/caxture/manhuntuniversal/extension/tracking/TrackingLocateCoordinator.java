package com.caxture.manhuntuniversal.extension.tracking;

import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Bounded provider work. A cancellation never claims an uncompleted provider's I/O permit. */
public final class TrackingLocateCoordinator {
    private static final long TIMEOUT = 10_000_000_000L;
    private final OwnedRegistry owned;
    private final LongSupplier time;
    private final Predicate<LocateRequest> current;
    private final Consumer<Completion> completed;
    private final Set<Job> jobs = new LinkedHashSet<>();
    private final Map<Key, Job> shared = new HashMap<>();
    private final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private final Map<Key, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private int inFlight;
    public record Completion(LocateRequest request, LocateResult result) { }
    public TrackingLocateCoordinator(OwnedRegistry owned, LongSupplier time, Predicate<LocateRequest> current, Consumer<Completion> completed) {
        this.owned = owned; this.time = time; this.current = current; this.completed = completed;
    }
    public boolean request(LocateRequest request, TrackingRegistryService.LocatorBinding binding) {
        owned.requireThread();
        if (request.targetRevision() != binding.revision() || !ownerValid(binding) || !isCurrent(request)) return false;
        Subscription old = subscriptions.get(request.playerId());
        if (old != null && old.request.equals(request) && old.job.binding.identity() == binding.identity()) return true;
        cancel(request.playerId());
        Key key = key(request, binding);
        Cached cached = key == null ? null : cache.get(key);
        if (cached != null && time.getAsLong() - cached.at < cached.ttl) { deliver(request, binding, cached.result); return true; }
        if (cached != null) cache.remove(key);
        Job job = key == null ? null : shared.get(key);
        if (job == null) {
            if (jobs.size() >= 128) return false;
            job = new Job(request, binding, key, time.getAsLong()); jobs.add(job);
            if (key != null) shared.put(key, job);
        }
        Subscription subscription = new Subscription(request, job);
        job.waiters.put(request.playerId(), subscription); subscriptions.put(request.playerId(), subscription);
        return true;
    }
    public void tick() {
        owned.requireThread(); reap();
        for (Job job : List.copyOf(jobs)) {
            if (time.getAsLong() - job.createdAt >= TIMEOUT && !job.cancelled.get()) {
                finishWaiters(job, LocateResult.failed("Tracking provider timed out")); cancelJob(job);
            }
            if (job.future == null && !ownerValid(job.binding)) cancelJob(job);
        }
        for (Job job : List.copyOf(jobs)) {
            if (inFlight >= 8) break;
            if (job.future != null || job.cancelled.get() || job.waiters.isEmpty()) continue;
            for (Subscription waiter : List.copyOf(job.waiters.values())) if (!isCurrent(waiter.request)) cancel(waiter.request.playerId());
            if (!jobs.contains(job) || job.waiters.isEmpty()) continue;
            job.future = new CompletableFuture<>(); inFlight++;
            try {
                CompletionStage<LocateResult> stage = owned.invoke(() -> job.binding.locator().locate(job.request, job.cancelled::get));
                if (stage == null) throw new IllegalStateException("Provider returned a null CompletionStage");
                stage.whenComplete((result, error) -> job.future.complete(error != null ? LocateResult.failed(error.toString())
                        : result == null ? LocateResult.failed("Provider returned null") : result));
            } catch (RuntimeException | LinkageError error) {
                owned.report(new IllegalStateException("Tracking provider failed: " + job.request.targetId(), error));
                job.future.complete(LocateResult.failed(error.toString()));
            }
        }
        reap();
    }
    private void reap() {
        for (Job job : List.copyOf(jobs)) {
            if (job.future == null || !job.future.isDone()) continue;
            LocateResult result;
            try { result = job.future.join(); } catch (RuntimeException error) { result = LocateResult.failed(error.toString()); }
            inFlight--; jobs.remove(job); if (job.key != null) shared.remove(job.key, job);
            if (job.cancelled.get()) continue;
            if (result.position().isPresent() && !result.position().get().worldId().equals(job.request.origin().worldId()))
                result = LocateResult.failed("Provider returned a different target world");
            if (job.key != null && ownerValid(job.binding) && result.status() != LocateResult.Status.FAILED && result.status() != LocateResult.Status.USE_CORE) {
                cache.put(job.key, new Cached(job.request, result, time.getAsLong(), result.status() == LocateResult.Status.FOUND ? 30_000_000_000L : 5_000_000_000L));
                while (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
            }
            finishWaiters(job, result);
        }
    }
    private void finishWaiters(Job job, LocateResult result) {
        for (Subscription subscription : List.copyOf(job.waiters.values())) {
            if (subscriptions.remove(subscription.request.playerId(), subscription)) deliver(subscription.request, job.binding, result);
        }
        job.waiters.clear();
    }
    private void deliver(LocateRequest request, TrackingRegistryService.LocatorBinding binding, LocateResult result) {
        if (!ownerValid(binding) || !isCurrent(request)) return;
        LocateResult finalResult = result.status() == LocateResult.Status.FAILED && binding.fallbackToCore() ? LocateResult.core() : result;
        owned.callback(() -> completed.accept(new Completion(request, finalResult)));
    }
    public void cancel(UUID player) {
        owned.requireThread(); Subscription subscription = subscriptions.remove(player); if (subscription == null) return;
        Job job = subscription.job; job.waiters.remove(player);
        if (job.waiters.isEmpty()) cancelJob(job);
    }
    private void cancelJob(Job job) {
        job.cancelled.set(true);
        for (Subscription waiter : List.copyOf(job.waiters.values())) subscriptions.remove(waiter.request.playerId(), waiter);
        job.waiters.clear();
        if (job.key != null) shared.remove(job.key, job);
        if (job.future == null) jobs.remove(job);
    }
    public void invalidate(Predicate<LocateRequest> scope) {
        owned.requireThread();
        for (Subscription subscription : List.copyOf(subscriptions.values())) if (scope.test(subscription.request)) cancel(subscription.request.playerId());
        cache.entrySet().removeIf(entry -> scope.test(entry.getValue().request));
    }
    public void clear() { invalidate(ignored -> true); }
    public int inFlight() { owned.requireThread(); return inFlight; }
    private boolean ownerValid(TrackingRegistryService.LocatorBinding binding) { return binding.owner().isEmpty() || owned.valid(binding.owner().get()); }
    private boolean isCurrent(LocateRequest request) {
        try { return current.test(request); } catch (RuntimeException error) { owned.report(error); return false; }
    }
    private Key key(LocateRequest request, TrackingRegistryService.LocatorBinding binding) {
        if (binding.sharedCacheKey().isEmpty()) return null;
        try {
            String token = owned.invoke(() -> binding.sharedCacheKey().get().apply(request));
            if (token == null || token.length() > 512) throw new IllegalArgumentException("Invalid shared tracking key");
            return new Key(new Identity(binding.identity()), request.sessionId(), request.targetId(), request.targetRevision(), request.viewer(), request.origin(), request.radiusBlocks(), token);
        } catch (RuntimeException error) { owned.report(error); return null; }
    }
    private record Key(Identity identity, UUID match, String target, long revision, com.caxture.manhuntuniversal.api.WorldPosition viewer,
                       com.caxture.manhuntuniversal.api.WorldPosition origin, int radius, String token) { }
    private static final class Identity {
        final Object value; Identity(Object value) { this.value = value; }
        @Override public boolean equals(Object other) { return other instanceof Identity identity && identity.value == value; }
        @Override public int hashCode() { return System.identityHashCode(value); }
    }
    private record Cached(LocateRequest request, LocateResult result, long at, long ttl) { }
    private record Subscription(LocateRequest request, Job job) { }
    private static final class Job {
        final LocateRequest request;
        final TrackingRegistryService.LocatorBinding binding;
        final Key key;
        final long createdAt;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Map<UUID, Subscription> waiters = new LinkedHashMap<>();
        CompletableFuture<LocateResult> future;
        Job(LocateRequest request, TrackingRegistryService.LocatorBinding binding, Key key, long createdAt) {
            this.request = request; this.binding = binding; this.key = key; this.createdAt = createdAt;
        }
    }
}
