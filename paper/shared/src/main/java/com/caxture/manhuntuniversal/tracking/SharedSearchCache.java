package com.caxture.manhuntuniversal.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Main-thread exact-input cache and cancellable multicast work registry. Values must be immutable. */
final class SharedSearchCache<K, V> {
    private final int capacity;
    private final long positiveTtl;
    private final long negativeTtl;
    private final LongSupplier nanoTime;
    private final Consumer<UUID> cancelWork;
    private final Map<K, Cached<V>> cached = new LinkedHashMap<>(16, .75f, true);
    private final Map<K, Work> active = new HashMap<>();
    private final Map<UUID, Work> subscriptions = new HashMap<>();

    SharedSearchCache(int capacity, long positiveTtl, long negativeTtl,
                      LongSupplier nanoTime, Consumer<UUID> cancelWork) {
        if (capacity < 1 || positiveTtl < 1 || negativeTtl < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.positiveTtl = positiveTtl;
        this.negativeTtl = negativeTtl;
        this.nanoTime = nanoTime;
        this.cancelWork = cancelWork;
    }

    /** Returns new work only to the first subscriber; a hit/join invokes or retains the callback. */
    Work request(UUID subscriber, K key, Consumer<Optional<V>> callback) {
        Work previous = subscriptions.get(subscriber);
        if (previous != null && previous.key.equals(key)) {
            previous.callbacks.put(subscriber, callback);
            return null;
        }
        cancel(subscriber);
        Cached<V> hit = cached.get(key);
        if (hit != null && nanoTime.getAsLong() - hit.createdAt < hit.ttl) {
            callback.accept(hit.value);
            return null;
        }
        cached.remove(key);
        Work work = active.get(key);
        boolean created = work == null;
        if (created) {
            work = new Work(key);
            active.put(key, work);
        }
        work.callbacks.put(subscriber, callback);
        subscriptions.put(subscriber, work);
        return created ? work : null;
    }

    void cancel(UUID subscriber) {
        Work work = subscriptions.remove(subscriber);
        if (work != null) {
            work.callbacks.remove(subscriber);
            if (work.callbacks.isEmpty()) discard(work);
        }
    }

    void invalidateMatching(Predicate<K> predicate) {
        cached.keySet().removeIf(predicate);
        for (Work work : new ArrayList<>(active.values())) {
            if (predicate.test(work.key)) discard(work);
        }
    }

    void clear() {
        cached.clear();
        for (Work work : new ArrayList<>(active.values())) discard(work);
    }

    int cachedCount() { return cached.size(); }

    private void discard(Work work) {
        active.remove(work.key, work);
        work.callbacks.keySet().forEach(subscriber -> subscriptions.remove(subscriber, work));
        work.callbacks.clear();
        cancelWork.accept(work.id);
    }

    final class Work {
        private final UUID id = UUID.randomUUID();
        private final K key;
        private final Map<UUID, Consumer<Optional<V>>> callbacks = new LinkedHashMap<>();

        private Work(K key) { this.key = key; }
        UUID id() { return id; }
        K key() { return key; }
        boolean active() { return SharedSearchCache.this.active.get(key) == this; }
        void complete(Optional<V> result) { finish(result, true); }
        void fail() { finish(Optional.empty(), false); }

        private void finish(Optional<V> result, boolean cacheResult) {
            if (!active()) return;
            if (cacheResult) {
                cached.put(key, new Cached<>(result, nanoTime.getAsLong(),
                        result.isPresent() ? positiveTtl : negativeTtl));
                while (cached.size() > capacity) cached.remove(cached.keySet().iterator().next());
            }
            var recipients = new ArrayList<>(callbacks.values());
            discard(this);
            RuntimeException failure = null;
            for (var recipient : recipients) {
                try { recipient.accept(result); }
                catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private record Cached<V>(Optional<V> value, long createdAt, long ttl) {}
}
