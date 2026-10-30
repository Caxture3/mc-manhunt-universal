package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.plugin.Plugin;

import java.util.*;

/** Registrations deliberately use identity rather than user-defined equals implementations. */
final class ExtensionRegistry {
    private final Runnable requireThread;
    private final Set<Entry> entries = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ControlDomain, Entry> domains = new EnumMap<>(ControlDomain.class);

    ExtensionRegistry(Runnable requireThread) { this.requireThread = requireThread; }

    RegistrationResult register(Plugin owner, ExtensionOptions options) {
        requireThread.run();
        if (owner == null || options == null || !owner.isEnabled()) {
            return rejected(OperationStatus.INVALID_ARGUMENT, "An enabled owner and options are required");
        }
        if (options.id().isBlank()
                || options.eliminationPolicy().isPresent() && !options.controls().contains(ControlDomain.ELIMINATION)
                || options.victoryPolicy().isPresent() && !options.controls().contains(ControlDomain.VICTORY)) {
            return rejected(OperationStatus.INVALID_ARGUMENT, "Policies require their control domains and a nonblank ID");
        }
        if (entries.stream().anyMatch(entry -> entry.id().equals(options.id()))
                || options.controls().stream().anyMatch(domains::containsKey)) {
            return rejected(OperationStatus.CONTROL_CONFLICT, "Extension ID or control domain is already registered");
        }
        Entry entry = new Entry(owner, options);
        entries.add(entry);
        options.controls().forEach(domain -> domains.put(domain, entry));
        return new RegistrationResult(OperationStatus.OK, Optional.of(entry), "Registered");
    }

    OperationStatus validate(ExtensionRegistration token, ControlDomain domain) {
        if (!(token instanceof Entry entry) || !entries.contains(entry) || !entry.active
                || !entry.owner.isEnabled()) {
            return OperationStatus.UNREGISTERED_EXTENSION;
        }
        return domains.get(domain) == entry ? OperationStatus.OK : OperationStatus.CONTROL_CONFLICT;
    }

    Optional<EliminationPolicy> eliminationPolicy() {
        return entry(ControlDomain.ELIMINATION).flatMap(value -> value.options.eliminationPolicy());
    }

    Optional<VictoryPolicy> victoryPolicy() {
        return entry(ControlDomain.VICTORY).flatMap(value -> value.options.victoryPolicy());
    }

    private Optional<Entry> entry(ControlDomain domain) {
        Entry value = domains.get(domain);
        return value != null && value.active && value.owner.isEnabled() ? Optional.of(value) : Optional.empty();
    }

    void unregisterOwner(Plugin owner) {
        requireThread.run();
        List.copyOf(entries).stream().filter(entry -> entry.owner == owner).forEach(Entry::close);
    }

    void clear() { requireThread.run(); List.copyOf(entries).forEach(Entry::close); }

    private static RegistrationResult rejected(OperationStatus status, String message) {
        return new RegistrationResult(status, Optional.empty(), message);
    }

    private final class Entry implements ExtensionRegistration {
        private final Plugin owner;
        private final ExtensionOptions options;
        private boolean active = true;

        private Entry(Plugin owner, ExtensionOptions options) { this.owner = owner; this.options = options; }
        @Override public String id() { return options.id(); }
        @Override public Set<ControlDomain> controls() { return options.controls(); }
        @Override public boolean active() { return active && owner.isEnabled(); }
        @Override public void close() {
            requireThread.run();
            if (!active) return;
            active = false;
            entries.remove(this);
            domains.entrySet().removeIf(value -> value.getValue() == this);
        }
    }
}
