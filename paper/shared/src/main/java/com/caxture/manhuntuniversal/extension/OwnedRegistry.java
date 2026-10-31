package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import java.util.*;
import java.util.function.*;

/** Shared main-thread gate and lifecycle for the public registry services. */
public final class OwnedRegistry {
    private final Runnable thread;
    private final Supplier<OperationStatus> externalGate;
    private final Predicate<ExtensionRegistration> valid;
    private final Consumer<RuntimeException> failure;
    private final Set<Handle> handles = Collections.newSetFromMap(new IdentityHashMap<>());
    private int depth;

    public OwnedRegistry(Runnable thread, Supplier<OperationStatus> externalGate,
                         Predicate<ExtensionRegistration> valid, Consumer<RuntimeException> failure) {
        this.thread = Objects.requireNonNull(thread); this.externalGate = Objects.requireNonNull(externalGate);
        this.valid = Objects.requireNonNull(valid); this.failure = Objects.requireNonNull(failure);
    }
    public void requireThread() { thread.run(); }
    public boolean busy() { return depth != 0; }
    public OperationStatus denial() {
        OperationStatus denied = externalGate.get();
        return denied != null ? denied : busy() ? OperationStatus.BUSY : null;
    }
    public boolean valid(ExtensionRegistration owner) { requireThread(); return owner != null && valid.test(owner); }

    public <T> ExtensionResult<T> execute(ExtensionRegistration owner, Supplier<ExtensionResult<T>> action) {
        OperationStatus denied = externalGate.get();
        if (denied != null) return ExtensionResult.rejected(denied, denied.name());
        requireThread();
        if (busy()) return ExtensionResult.rejected(OperationStatus.BUSY, "Nested registry mutation");
        if (!valid(owner)) return ExtensionResult.rejected(OperationStatus.UNREGISTERED_EXTENSION, "Invalid owner");
        return invoke(action);
    }
    /** Guards extension callbacks, including callbacks invoked from read/display operations. */
    public <T> T invoke(Supplier<T> action) {
        requireThread(); depth++;
        try { return action.get(); } finally { depth--; }
    }
    public void callback(Runnable action) {
        try { invoke(() -> { action.run(); return null; }); }
        catch (RuntimeException | LinkageError error) { report(error); }
    }
    public void report(Throwable error) {
        failure.accept(error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error));
    }
    public ExtensionHandle add(ExtensionRegistration owner, Runnable cleanup) {
        return add(owner, () -> true, cleanup);
    }
    public ExtensionHandle add(ExtensionRegistration owner, BooleanSupplier registered, Runnable cleanup) {
        requireThread();
        if (!valid(owner)) throw new IllegalStateException("Invalid registration owner");
        var handle = new Handle(owner, Objects.requireNonNull(registered), Objects.requireNonNull(cleanup));
        handles.add(handle);
        return handle;
    }
    public void closeOwner(ExtensionRegistration owner) {
        requireThread();
        List.copyOf(handles).stream().filter(handle -> handle.owner == owner).forEach(Handle::dispose);
    }
    public void clear() { requireThread(); List.copyOf(handles).forEach(Handle::dispose); }

    private final class Handle implements ExtensionHandle {
        private final ExtensionRegistration owner;
        private final Runnable cleanup;
        private final BooleanSupplier registered;
        private boolean active = true;
        Handle(ExtensionRegistration owner, BooleanSupplier registered, Runnable cleanup) { this.owner = owner; this.registered = registered; this.cleanup = cleanup; }
        @Override public boolean active() { requireThread(); return active && valid(owner) && registered.getAsBoolean(); }
        @Override public void close() {
            requireThread();
            if (!active) return;
            if (busy()) throw new IllegalStateException("Cannot close a registry handle during a callback");
            dispose();
        }
        private void dispose() {
            if (!active) return;
            active = false; handles.remove(this); callback(cleanup);
        }
    }
}
