package com.caxture.manhuntuniversal.tracking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class TrackingSearchQueue {
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Key, Long> currentVersions = new HashMap<>();
    private long nextVersion;

    Request replace(UUID playerId, Lane lane) {
        long version = ++nextVersion;
        Key key = new Key(playerId, lane);
        currentVersions.put(key, version);
        entries.removeIf(entry -> entry.request().key().equals(key));
        return new Request(key, version);
    }

    void add(Request request, Runnable operation) {
        entries.addLast(new Entry(request, operation));
    }

    void invalidate(UUID playerId) {
        currentVersions.keySet().removeIf(key -> key.playerId().equals(playerId));
        entries.removeIf(entry -> entry.request().key().playerId().equals(playerId));
    }

    void invalidate(UUID playerId, Lane lane) {
        Key key = new Key(playerId, lane);
        currentVersions.remove(key);
        entries.removeIf(entry -> entry.request().key().equals(key));
    }

    boolean runOne() {
        Entry entry;
        while ((entry = entries.pollFirst()) != null) {
            if (currentVersions.getOrDefault(entry.request().key(), -1L)
                    != entry.request().version()) {
                continue;
            }
            entry.operation().run();
            return true;
        }
        return false;
    }

    int pendingCount() {
        return entries.size();
    }

    void clear() {
        entries.clear();
        currentVersions.clear();
        nextVersion = 0;
    }

    enum Lane {
        STATIC,
        PORTAL
    }

    record Request(Key key, long version) {
    }

    private record Key(UUID playerId, Lane lane) {
    }

    private record Entry(Request request, Runnable operation) {
    }
}
