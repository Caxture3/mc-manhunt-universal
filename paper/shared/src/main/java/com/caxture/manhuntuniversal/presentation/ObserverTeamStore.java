package com.caxture.manhuntuniversal.presentation;

import java.util.*;
import java.util.function.Predicate;

/** Keeps retry information until the corresponding packet operations have succeeded. */
final class ObserverTeamStore<K, T> {
    @FunctionalInterface interface Sender<T> { void send(T value, boolean create) throws ReflectiveOperationException; }
    @FunctionalInterface interface Restorer<T> { void send(T value) throws ReflectiveOperationException; }
    private static final class State<T> {
        final T value;
        boolean created;
        State(T value) { this.value = value; }
    }
    private final Map<K, State<T>> entries = new HashMap<>();
    T get(K key) { State<T> state = entries.get(key); return state == null ? null : state.value; }
    void put(K key, T value) { entries.put(key, new State<>(value)); }
    void send(K key, Sender<T> sender) throws ReflectiveOperationException {
        State<T> state = Objects.requireNonNull(entries.get(key));
        sender.send(state.value, !state.created);
        state.created = true;
    }
    void restore(K key, Restorer<T> remove, Restorer<T> original) throws ReflectiveOperationException {
        State<T> state = entries.get(key);
        if (state == null) return;
        if (state.created) {
            remove.send(state.value);
            state.created = false;
        }
        original.send(state.value);
        entries.remove(key, state);
    }
    void forget(Predicate<K> predicate) { entries.keySet().removeIf(predicate); }
    boolean closeIfRestored(Runnable close) {
        if (!entries.isEmpty()) return false;
        close.run();
        return true;
    }
}
