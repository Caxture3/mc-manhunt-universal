package com.caxture.randomanhunt.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class PendingResetRetrier {
    private final Scheduler scheduler;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public PendingResetRetrier(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void start(UUID playerId, Supplier<Outcome> attempt) {
        if (entries.containsKey(playerId)) {
            return;
        }
        Entry entry = new Entry();
        entries.put(playerId, entry);
        entry.task = scheduler.repeat(() -> {
            if (entries.get(playerId) != entry) {
                return;
            }
            if (attempt.get() != Outcome.RETRY) {
                cancel(playerId);
            }
        });
    }

    public boolean isScheduled(UUID playerId) {
        return entries.containsKey(playerId);
    }

    public void cancel(UUID playerId) {
        Entry entry = entries.remove(playerId);
        if (entry != null && entry.task != null) {
            entry.task.cancel();
        }
    }

    public void cancelAll() {
        for (Entry entry : entries.values()) {
            if (entry.task != null) {
                entry.task.cancel();
            }
        }
        entries.clear();
    }

    public enum Outcome {
        RETRY,
        COMPLETE,
        CANCEL
    }

    @FunctionalInterface
    public interface Scheduler {
        Cancellable repeat(Runnable action);
    }

    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }

    private static final class Entry {
        private Cancellable task;
    }
}
