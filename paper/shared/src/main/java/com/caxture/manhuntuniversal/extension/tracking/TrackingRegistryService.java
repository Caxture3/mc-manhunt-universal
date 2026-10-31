package com.caxture.manhuntuniversal.extension.tracking;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import java.util.*;
import java.util.function.*;

/** Server-authoritative catalog. Pure DTOs cross the API boundary, never protocol enums. */
public final class TrackingRegistryService implements TrackingRegistry {
    public interface Runtime {
        Optional<TrackingViewerContext> viewer(UUID player);
        boolean maySelect(UUID player);
        Optional<String> selection(UUID player);
        void selection(UUID player, String target);
        Optional<TrackingSnapshot> snapshot(UUID player);
        void refresh(UUID player);
        void invalidate(TrackingInvalidation scope);
        void catalogChanged();
    }
    public record LocatorBinding(TrackingLocator locator, Object identity, Optional<ExtensionRegistration> owner,
                                 boolean fallbackToCore, Optional<Function<LocateRequest, String>> sharedCacheKey, long revision) { }
    private record Definition(ExtensionRegistration owner, TrackingTargetDefinition value) { }
    private record Locator(ExtensionRegistration owner, String target, int priority, TrackingLocator provider) { }
    private record Policy(ExtensionRegistration owner, String target, int priority, TrackingAvailabilityPolicy provider) { }
    private record ViewerTarget(UUID player, String target) { }
    private final OwnedRegistry owned;
    private final Runtime runtime;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final List<Locator> locators = new ArrayList<>();
    private final List<Policy> policies = new ArrayList<>();
    private final Map<ViewerTarget, TargetAvailability> previous = new HashMap<>();
    private final Set<ViewerTarget> evaluating=new HashSet<>();
    private final Set<UUID> knownPlayers = new HashSet<>();
    private Consumer<TrackingChange> changed = ignored -> {};
    private long revision;
    @FunctionalInterface public interface Labels{String resolve(TrackingViewerContext viewer,String target,String field,LocalizedText fallback);}
    private Labels labels=(viewer,target,field,fallback)->fallback.resolve(viewer.locale());
    public void labels(Labels resolver){owned.requireThread();labels=Objects.requireNonNull(resolver);}
    public TrackingRegistryService(OwnedRegistry owned, Runtime runtime) { this.owned = owned; this.runtime = runtime; }
    public void onChange(Consumer<TrackingChange> listener) { owned.requireThread(); changed = listener; }
    public long revision() { owned.requireThread(); return revision; }
    @Override public List<TrackingTargetInfo> list() {
        owned.requireThread();
        return definitions.values().stream().map(entry -> new TrackingTargetInfo(entry.value.id(), entry.owner == null ? "core" : entry.owner.id(),
                entry.value.name(), entry.value.secondaryName(), entry.value.description(), entry.value.order(), entry.value.world(), entry.value.appearance()))
                .sorted(Comparator.comparingInt(TrackingTargetInfo::order).thenComparing(TrackingTargetInfo::id)).toList();
    }
    public void registerCore(TrackingTargetDefinition definition) {
        owned.requireThread();
        if (!definition.id().startsWith("core:") || definitions.size() >= 32 || definitions.putIfAbsent(definition.id(), new Definition(null, definition)) != null)
            throw new IllegalArgumentException("Invalid or duplicate core target");
        revision++;
    }
    @Override public ExtensionResult<ExtensionHandle> register(ExtensionRegistration owner, TrackingTargetDefinition definition) {
        return owned.execute(owner, () -> {
            if (definition == null || definition.id().startsWith("core:")) return invalid("Reserved or missing target definition");
            if (definitions.containsKey(definition.id())) return conflict("Target ID already registered");
            if (definitions.size() >= 32) return invalid("At most 32 tracking targets may be registered");
            var entry = new Definition(owner, definition); definitions.put(definition.id(), entry); catalogChanged();
            return ExtensionResult.ok(owned.add(owner, () -> definitions.get(definition.id()) == entry, () -> {
                definitions.remove(definition.id(), entry); locators.removeIf(value -> value.target.equals(definition.id()));
                policies.removeIf(value -> value.target.equals(definition.id()));
                previous.keySet().removeIf(value -> value.target.equals(definition.id()));
                for (UUID player : List.copyOf(knownPlayers)) if (runtime.selection(player).filter(definition.id()::equals).isPresent())
                    changeSelection(player, null, TrackingChange.Source.REGISTRATION);
                catalogChanged();
            }));
        });
    }
    @Override public ExtensionResult<ExtensionHandle> replaceLocator(ExtensionRegistration owner, String target, int priority, TrackingLocator provider) {
        return owned.execute(owner, () -> {
            if (!definitions.containsKey(target) || provider == null) return invalid("Unknown target or missing provider");
            if (locators.stream().anyMatch(value -> value.target.equals(target) && value.priority == priority)) return conflict("Same-priority locator");
            var entry = new Locator(owner, target, priority, provider); locators.add(entry); catalogChanged();
            return ExtensionResult.ok(owned.add(owner, () -> locators.contains(entry), () -> { locators.remove(entry); catalogChanged(); }));
        });
    }
    @Override public ExtensionResult<ExtensionHandle> availability(ExtensionRegistration owner, String target, int priority, TrackingAvailabilityPolicy policy) {
        return owned.execute(owner, () -> {
            if (!definitions.containsKey(target) || policy == null) return invalid("Unknown target or missing availability policy");
            if (policies.stream().anyMatch(value -> value.target.equals(target) && value.priority == priority)) return conflict("Same-priority availability policy");
            var entry = new Policy(owner, target, priority, policy); policies.add(entry); catalogChanged();
            return ExtensionResult.ok(owned.add(owner, () -> policies.contains(entry), () -> { policies.remove(entry); catalogChanged(); }));
        });
    }
    @Override public Optional<TrackingMenuSnapshot> menu(UUID player) { return menu(player, TrackingChange.Source.NATURAL); }
    public Optional<TrackingMenuSnapshot> menu(UUID player, TrackingChange.Source source) {
        owned.requireThread(); var viewer = runtime.viewer(player).orElse(null);
        if (viewer == null) return Optional.empty(); knownPlayers.add(player);
        List<TrackingOptionSnapshot> options = new ArrayList<>();
        for (var entry : definitions.values().stream().sorted(Comparator.comparingInt((Definition value) -> value.value.order()).thenComparing(value -> value.value.id())).toList()) {
            TargetAvailability available = availability(entry, viewer, source);
            if (available.visible()) options.add(new TrackingOptionSnapshot(entry.value.id(), labels.resolve(viewer,entry.value.id(),"name",entry.value.name()),
                    labels.resolve(viewer,entry.value.id(),"secondary-name",entry.value.secondaryName()),labels.resolve(viewer,entry.value.id(),"description",entry.value.description()),
                    entry.value.order(), entry.value.appearance(), available));
        }
        return Optional.of(new TrackingMenuSnapshot(revision, runtime.selection(player), options));
    }
    public TargetAvailability availability(String target, UUID player, TrackingChange.Source source) {
        owned.requireThread(); Definition definition = definitions.get(target); var viewer = runtime.viewer(player).orElse(null);
        return definition == null || viewer == null ? TargetAvailability.hidden() : availability(definition, viewer, source);
    }
    private TargetAvailability availability(Definition entry, TrackingViewerContext viewer, TrackingChange.Source source) {
        var key = new ViewerTarget(viewer.playerId(), entry.value.id());
        if(!evaluating.add(key))return previous.getOrDefault(key,TargetAvailability.hidden());
        if(entry.owner!=null&&!owned.valid(entry.owner)){evaluating.remove(key);return TargetAvailability.hidden();}
        Policy policy = policies.stream().filter(value -> owned.valid(value.owner)&&value.target.equals(entry.value.id())).max(Comparator.comparingInt(value -> value.priority)).orElse(null);
        TargetAvailability current;
        try { current = Objects.requireNonNull(owned.invoke(() -> (policy == null ? entry.value.availability() : policy.provider).evaluate(viewer))); }
        catch (RuntimeException | LinkageError error) { owned.report(new IllegalStateException("Tracking availability failed: " + entry.value.id(), error)); current = TargetAvailability.hidden(); }
        finally{evaluating.remove(key);}
        TargetAvailability old = previous.put(key, current);
        if (old != null && !old.sameState(current)) {
            emit(new TrackingChange(TrackingChange.Kind.AVAILABILITY, viewer.playerId(), entry.value.id(), Optional.empty(), Optional.of(old), Optional.of(current), source));
        }
        if (!current.selectable() && runtime.selection(viewer.playerId()).filter(entry.value.id()::equals).isPresent())changeSelection(viewer.playerId(),null,source);
        return current;
    }
    @Override public Optional<TrackingSnapshot> snapshot(UUID player) { owned.requireThread(); return runtime.viewer(player).isEmpty() ? Optional.empty() : Optional.of(currentSnapshot(player)); }
    @Override public ExtensionResult<TrackingSnapshot> select(TrackingMutation mutation, String target) {
        if (mutation == null) return invalid("Mutation required");
        return owned.execute(mutation.context().extension(), () -> {
            var denied = validate(mutation); if (denied != null) return denied;
            if (!definitions.containsKey(target)) return invalid("Unknown tracking target");
            if (!availability(target, mutation.playerId(), TrackingChange.Source.EXTENSION).selectable()) return invalid("Tracking target is locked or hidden");
            changeSelection(mutation.playerId(), target, TrackingChange.Source.EXTENSION); return ExtensionResult.ok(currentSnapshot(mutation.playerId()));
        });
    }
    @Override public ExtensionResult<TrackingSnapshot> clear(TrackingMutation mutation) {
        if (mutation == null) return invalid("Mutation required");
        return owned.execute(mutation.context().extension(), () -> {
            var denied = validate(mutation); if (denied != null) return denied;
            changeSelection(mutation.playerId(), null, TrackingChange.Source.EXTENSION); return ExtensionResult.ok(currentSnapshot(mutation.playerId()));
        });
    }
    @Override public ExtensionResult<TrackingSnapshot> refresh(TrackingMutation mutation) {
        if (mutation == null) return invalid("Mutation required");
        return owned.execute(mutation.context().extension(), () -> {
            var denied = validate(mutation); if (denied != null) return denied;
            runtime.refresh(mutation.playerId()); return ExtensionResult.ok(currentSnapshot(mutation.playerId()));
        });
    }
    @Override public ExtensionResult<Void> invalidate(ExtensionRegistration owner, TrackingInvalidation scope) {
        return owned.execute(owner, () -> {
            if (scope == null || scope.targetId().filter(id -> !definitions.containsKey(id)).isPresent()) return invalid("Unknown invalidation scope");
            runtime.invalidate(scope);
            if (scope.availabilityChanged()) for (UUID player : List.copyOf(knownPlayers))
                if (scope.playerId().isEmpty() || scope.playerId().get().equals(player)) menu(player, TrackingChange.Source.EXTENSION);
            return ExtensionResult.ok(null);
        });
    }
    /** User-originated requests have already passed session/authorization validation in the owning menu controller. */
    public boolean choose(UUID player, String target, long directoryRevision) {
        owned.requireThread();
        if (directoryRevision != revision || !eligible(player) || !definitions.containsKey(target)
                || !availability(target, player, TrackingChange.Source.PLAYER_STATE).selectable()) return false;
        changeSelection(player, runtime.selection(player).filter(target::equals).isPresent() ? null : target, TrackingChange.Source.PLAYER_STATE);
        return true;
    }
    public Optional<TrackingTargetDefinition> definition(String target) { owned.requireThread(); return Optional.ofNullable(definitions.get(target)).map(Definition::value); }
    public Optional<LocatorBinding> locator(String target) {
        owned.requireThread(); Definition definition = definitions.get(target); if (definition == null) return Optional.empty();
        Locator replacement = locators.stream().filter(value -> owned.valid(value.owner)&&value.target.equals(target)).max(Comparator.comparingInt(value -> value.priority)).orElse(null);
        return Optional.of(replacement == null ? new LocatorBinding(definition.value.locator(), definition, Optional.ofNullable(definition.owner),
                definition.value.fallbackToCore(), definition.value.sharedCacheKey(), revision)
                : new LocatorBinding(replacement.provider, replacement, Optional.of(replacement.owner), definition.value.fallbackToCore(), Optional.empty(), revision));
    }
    public boolean customLocator(String target) { owned.requireThread(); return definitions.containsKey(target)
            && (!target.startsWith("core:") || locators.stream().anyMatch(value -> value.target.equals(target))); }
    public void forget(UUID player) { owned.requireThread(); knownPlayers.remove(player); previous.keySet().removeIf(key -> key.player.equals(player)); }
    public void clearMatch() { owned.requireThread(); previous.clear(); knownPlayers.clear(); }
    private ExtensionResult<TrackingSnapshot> validate(TrackingMutation mutation) {
        var viewer = runtime.viewer(mutation.playerId()).orElse(null);
        if (viewer == null || viewer.match().isEmpty()) return ExtensionResult.rejected(OperationStatus.NO_ACTIVE_MATCH, "No active match");
        var match = viewer.match().get();
        if (!match.id().equals(mutation.context().sessionId())) return ExtensionResult.rejected(OperationStatus.STALE_SESSION, "Match changed");
        if (mutation.context().expectedRevision() >= 0 && mutation.context().expectedRevision() != match.revision()
                || mutation.expectedDirectoryRevision() >= 0 && mutation.expectedDirectoryRevision() != revision)
            return ExtensionResult.rejected(OperationStatus.STALE_REVISION, "Match or target directory changed");
        return eligible(mutation.playerId()) ? null : ExtensionResult.rejected(OperationStatus.PLAYER_UNAVAILABLE, "Player cannot use runner tracking");
    }
    private boolean eligible(UUID player) {
        var viewer = runtime.viewer(player).orElse(null); if (viewer == null || viewer.match().isEmpty() || !runtime.maySelect(player)) return false;
        var match = viewer.match().get(); var participant = match.participants().get(player);
        return match.state() == MatchState.RUNNING && participant != null && participant.role() == TeamRole.RUNNER && !participant.eliminated();
    }
    private void changeSelection(UUID player, String target, TrackingChange.Source source) {
        Optional<String> old = runtime.selection(player); if (Objects.equals(old.orElse(null), target)) return;
        runtime.selection(player, target); knownPlayers.add(player);
        emit(new TrackingChange(TrackingChange.Kind.SELECTION, player, target == null ? "" : target, old, Optional.empty(), Optional.empty(), source));
    }
    private TrackingSnapshot currentSnapshot(UUID player) {
        return runtime.snapshot(player).orElseGet(() -> new TrackingSnapshot(player, runtime.selection(player),
                runtime.selection(player).isPresent() ? TrackingStatus.SEARCHING : TrackingStatus.IDLE,
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), "", "", revision));
    }
    private void emit(TrackingChange change) { owned.callback(() -> changed.accept(change)); }
    private void catalogChanged() { revision++; runtime.invalidate(new TrackingInvalidation(Optional.empty(), Optional.empty(), false)); owned.callback(runtime::catalogChanged); }
    private static <T> ExtensionResult<T> invalid(String message) { return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, message); }
    private static <T> ExtensionResult<T> conflict(String message) { return ExtensionResult.rejected(OperationStatus.CONTROL_CONFLICT, message); }
}
