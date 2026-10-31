package com.caxture.manhuntuniversal.extension.config;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import java.util.*;
import java.util.function.*;

/** Typed roots own atomic storage; leaf paths are views into the same transaction. */
public final class ConfigurationRegistryService implements ConfigurationRegistry {
    private final OwnedRegistry owned;
    private final Map<String, ConfigDefinition<?>> definitions = new LinkedHashMap<>();
    private final Map<String, Object> effective = new HashMap<>();
    private final List<Layer> layers = new ArrayList<>();
    private Consumer<Set<String>> changed;
    private long revision;
    private UUID activeMatch;
    private final Map<String,Object> matchValues = new HashMap<>();
    private record Watcher(ExtensionRegistration owner, Consumer<ConfigurationChange> listener) { }
    private final List<Watcher> watchers = new ArrayList<>();
    public ConfigurationRegistryService(OwnedRegistry owned, Consumer<Set<String>> changed) {
        this.owned = owned; this.changed = changed;
    }
    public void onChange(Consumer<Set<String>> listener) { owned.requireThread(); this.changed = listener; }
    @Override public ExtensionResult<ExtensionHandle> listen(ExtensionRegistration owner, Consumer<ConfigurationChange> listener) {
        return owned.execute(owner, () -> {
            if (listener == null) return invalid("Listener required");
            var watcher = new Watcher(owner,listener); watchers.add(watcher);
            return ExtensionResult.ok(owned.add(owner, () -> watchers.remove(watcher)));
        });
    }
    public void beginMatch(UUID match) {
        owned.requireThread(); if (match.equals(activeMatch)) return; activeMatch=match; matchValues.clear();
        definitions.forEach((key, definition) -> { if (definition.timing()==ConfigTiming.NEXT_MATCH) matchValues.put(key,effective.get(key)); }); revision++;
    }
    public Map<String,Object> frozenCoreSnapshot() {
        owned.requireThread();Map<String,Object> saved=new LinkedHashMap<>();
        if(activeMatch!=null)matchValues.forEach((key,value)->{if(!key.contains(":"))saved.put(key,ConfigTypes.immutable(value));});return Map.copyOf(saved);
    }
    public void restoreMatch(UUID match,Map<String,Object> saved) {
        owned.requireThread();Map<String,Object> checked=new LinkedHashMap<>();
        Set<String> expected=new HashSet<>();definitions.forEach((key,definition)->{if(!key.contains(":")&&definition.timing()==ConfigTiming.NEXT_MATCH)expected.add(key);});
        if(!saved.keySet().equals(expected))throw new IllegalArgumentException("Incomplete frozen core configuration");
        saved.forEach((key,value)->checked.put(key,ConfigTypes.immutable(definitions.get(key).type().validate(value))));
        beginMatch(match);matchValues.putAll(checked);revision++;
    }
    public void endMatch(UUID match) {
        owned.requireThread(); if (!match.equals(activeMatch)) return;
        Set<String> changes=new HashSet<>(); matchValues.forEach((key,value)-> {if(!Objects.equals(value,effective.get(key)))changes.add(key);});
        activeMatch=null;matchValues.clear();revision++;if(!changes.isEmpty())notifyChanged(Set.copyOf(changes));
    }
    @Override public List<ConfigSnapshot> list() {
        owned.requireThread(); return definitions.keySet().stream().map(this::snapshot).toList();
    }
    @Override public Optional<ConfigSnapshot> find(String key) {
        owned.requireThread(); return path(key) == null ? Optional.empty() : Optional.of(snapshot(key));
    }
    public void registerCore(ConfigDefinition<?> definition) {
        owned.requireThread();
        if (definitions.putIfAbsent(definition.key(), definition) != null) throw new IllegalStateException("Duplicate core key");
        effective.put(definition.key(), read(definition));
    }
    @Override public <T> ExtensionResult<ExtensionHandle> register(ExtensionRegistration owner, ConfigDefinition<T> definition) {
        return owned.execute(owner, () -> {
            if (definition == null || !definition.key().contains(":")) return invalid("Extension config requires a namespace");
            if (definitions.containsKey(definition.key())) return conflict("Configuration key exists");
            registerCore(definition); revision++;
            if(activeMatch!=null && definition.timing()==ConfigTiming.NEXT_MATCH)matchValues.put(definition.key(),definition.defaultValue());
            var handle = owned.add(owner, () -> {
                definitions.remove(definition.key()); effective.remove(definition.key()); matchValues.remove(definition.key());
                layers.removeIf(layer -> overlap(layer.key, definition.key())); revision++;
                notifyChanged(Set.of(definition.key()));
            });
            notifyChanged(Set.of(definition.key()));
            return ExtensionResult.ok(handle);
        });
    }
    @Override public ExtensionResult<ConfigSnapshot> update(ExtensionRegistration owner, String key, Object value, long expected) {
        return owned.execute(owner, () -> updateInternal(key, value, expected));
    }
    /** Command gateway: permission is checked by the command, registry lock/revision rules still apply. */
    public ExtensionResult<ConfigSnapshot> updateCommand(String key, Object value) {
        owned.requireThread();
        if (owned.denial() != null) return ExtensionResult.rejected(owned.denial(), "Registry mutation unavailable");
        return owned.invoke(() -> updateInternal(key, value, -1));
    }
    private ExtensionResult<ConfigSnapshot> updateInternal(String key, Object value, long expected) {
        Path path = path(key);
        if (path == null) return invalid("Unknown configuration: " + key);
        if (expected >= 0 && expected != revision) return ExtensionResult.rejected(OperationStatus.STALE_REVISION, "Configuration changed");
        Layer lock = layers.stream().filter(layer -> owned.valid(layer.owner) && layer.reason != null && overlap(key, layer.key))
                .max(Comparator.comparingInt(layer -> layer.priority)).orElse(null);
        if (lock != null) return conflict("Locked by " + lock.owner.id() + ": " + lock.reason);
        try {
            Object replacement = path.replace(read(path.definition), value);
            if (!save(path.definition, replacement)) return ExtensionResult.rejected(OperationStatus.SERVICE_UNAVAILABLE, "Configuration save failed");
            revision++; recompute(path.definition.key());
            return ExtensionResult.ok(snapshot(key));
        } catch (RuntimeException exception) {
            return invalid(exception.getMessage() == null ? "Invalid value or storage failure" : exception.getMessage());
        }
    }
    @Override public ExtensionResult<ExtensionHandle> lock(ExtensionRegistration owner, String key, int priority, String reason) {
        return owned.execute(owner, () -> addLayer(owner, key, priority, Objects.requireNonNull(reason), null));
    }
    @Override public ExtensionResult<ExtensionHandle> override(ExtensionRegistration owner, String key,
                                                             Function<ConfigSnapshot, Object> provider, int priority) {
        return owned.execute(owner, () -> addLayer(owner, key, priority, null, Objects.requireNonNull(provider)));
    }
    private ExtensionResult<ExtensionHandle> addLayer(ExtensionRegistration owner, String key, int priority,
                                                     String reason, Function<ConfigSnapshot, Object> provider) {
        Path path = path(key);
        if (path == null) return invalid("Unknown config key");
        if (layers.stream().anyMatch(layer -> layer.priority == priority && (layer.reason == null) == (reason == null)
                && overlap(layer.key, key))) return conflict("Same-priority overlapping config layer");
        var layer = new Layer(owner, key, priority, reason, provider);
        if (provider != null) try { path.replace(read(path.definition), owned.invoke(() -> provider.apply(snapshot(key)))); }
        catch (RuntimeException exception) { return invalid("Invalid override: " + exception.getMessage()); }
        layers.add(layer); revision++; recompute(path.definition.key());
        return ExtensionResult.ok(owned.add(owner, () -> layers.contains(layer), () -> {
            layers.remove(layer); revision++;
            if (definitions.containsKey(path.definition.key())) recompute(path.definition.key());
        }));
    }
    @Override public ExtensionResult<ConfigSnapshot> refresh(ExtensionRegistration owner, String key) {
        return owned.execute(owner, () -> {
            Path path = path(key); if (path == null) return invalid("Unknown config key");
            revision++; recompute(path.definition.key()); return ExtensionResult.ok(snapshot(key));
        });
    }
    public Object parse(String key, String raw) {
        owned.requireThread(); Path path = path(key);
        if (path == null) throw new IllegalArgumentException("Unknown config key");
        return owned.invoke(() -> path.type().parse(raw));
    }
    public List<String> complete(String key, String prefix) {
        owned.requireThread(); Path path = path(key); return path == null ? List.of() : owned.invoke(() -> path.type().complete(prefix));
    }
    public List<String> keys() {
        owned.requireThread(); List<String> result = new ArrayList<>();
        definitions.forEach((key, definition) -> { result.add(key); collectKeys(key, definition.type(), result); });
        return List.copyOf(result);
    }
    private void collectKeys(String key, ConfigType<?> type, List<String> into) {
        type.fields().forEach((name, field) -> { String child = key + "." + name; into.add(child); collectKeys(child, field.type(), into); });
    }
    private void recompute(String root) {
        ConfigDefinition<?> definition = definitions.get(root);
        Object previous = effective.get(root), next = read(definition);
        var candidates=layers.stream().filter(layer->owned.valid(layer.owner)&&layer.provider!=null&&rootOf(layer.key).equals(root)).toList();
        List<String> leafKeys=new ArrayList<>();leafKeys.add(root);collectKeys(root,definition.type(),leafKeys);
        leafKeys.removeIf(key->!path(key).type().fields().isEmpty());
        for (Layer layer : candidates.stream().filter(candidate->leafKeys.stream().anyMatch(key->ancestor(candidate.key,key)
                        && candidates.stream().filter(other->ancestor(other.key,key)).noneMatch(other->other.priority>candidate.priority)))
                .sorted(Comparator.comparingInt((Layer layer) -> layer.priority).thenComparing(layer->layer.key)).toList()) {
            try {
                ConfigSnapshot metadata=snapshot(layer.key);Object underlying=path(layer.key).extract(next);
                ConfigSnapshot input=new ConfigSnapshot(metadata.key(),metadata.name(),metadata.description(),metadata.savedValue(),underlying,underlying,
                        metadata.revision(),metadata.timing(),metadata.unit(),metadata.booleanValue(),metadata.lockedBy(),metadata.lockReason());
                Object value = owned.invoke(() -> layer.provider.apply(input));
                next = path(layer.key).replace(next, value);
            } catch (RuntimeException | LinkageError error) { owned.report(new IllegalStateException("Config override failed: " + layer.owner.id(), error)); }
        }
        effective.put(root, next);
        if (!Objects.equals(previous, next)) {
            Set<String> keys = new LinkedHashSet<>(); keys.add(root);
            List<String> descendants = new ArrayList<>(); collectKeys(root, definition.type(), descendants);
            for (String key : descendants) {
                Path leaf = path(key);
                if (previous == null || !Objects.equals(leaf.extract(previous), leaf.extract(next))) keys.add(key);
            }
            notifyChanged(Set.copyOf(keys));
        }
    }
    private void notifyChanged(Set<String> keys) {
        owned.callback(() -> changed.accept(keys));
        Map<String,ConfigSnapshot> values=new LinkedHashMap<>(); for(String key:keys)if(path(key)!=null)values.put(key,snapshot(key));
        var event=new ConfigurationChange(revision,keys,values);
        for(Watcher watcher:List.copyOf(watchers))if(owned.valid(watcher.owner))owned.callback(()->watcher.listener.accept(event));
    }
    private ConfigSnapshot snapshot(String key) {
        Path path = path(key); var definition = path.definition;
        Layer lock = layers.stream().filter(layer -> owned.valid(layer.owner) && layer.reason != null && ancestor(layer.key, key))
                .max(Comparator.comparingInt(layer -> layer.priority)).orElse(null);
        Object saved = path.extract(read(definition));
        Object current = path.extract(effective.getOrDefault(definition.key(), read(definition)));
        Object active = matchValues.containsKey(definition.key()) ? path.extract(matchValues.get(definition.key())) : current;
        return new ConfigSnapshot(key, path.name(), definition.description(), saved, active, current, revision,
                definition.timing(), path.unit(), path.type().booleanValue(),
                Optional.ofNullable(lock).map(layer -> layer.owner.id()), Optional.ofNullable(lock).map(layer -> layer.reason));
    }
    private Object read(ConfigDefinition<?> definition) {
        try { return ConfigTypes.immutable(owned.invoke(() -> definition.type().validate(definition.storage().read()))); }
        catch (RuntimeException error) { owned.report(error); return ConfigTypes.immutable(definition.defaultValue()); }
    }
    @SuppressWarnings("unchecked") private boolean save(ConfigDefinition<?> definition, Object value) {
        return ((ConfigStorage<Object>) definition.storage()).save(definition.type().validate(value));
    }
    private Path path(String key) {
        if (key == null) return null;
        String root = rootOf(key); ConfigDefinition<?> definition = definitions.get(root);
        if (definition == null) return null;
        List<String> fields = root.equals(key) ? List.of() : List.of(key.substring(root.length() + 1).split("\\."));
        ConfigType<?> type = definition.type();
        for (String field : fields) { ConfigField<?> metadata = type.fields().get(field); if (metadata == null) return null; type = metadata.type(); }
        return new Path(definition, fields);
    }
    private String rootOf(String key) {
        return definitions.keySet().stream().filter(root -> ancestor(root, key)).max(Comparator.comparingInt(String::length)).orElse(key);
    }
    private static boolean ancestor(String first, String second) { return second.equals(first) || second.startsWith(first + "."); }
    private static boolean overlap(String first, String second) { return ancestor(first, second) || ancestor(second, first); }
    private static <T> ExtensionResult<T> invalid(String message) { return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, message); }
    private static <T> ExtensionResult<T> conflict(String message) { return ExtensionResult.rejected(OperationStatus.CONTROL_CONFLICT, message); }
    private record Layer(ExtensionRegistration owner, String key, int priority, String reason, Function<ConfigSnapshot, Object> provider) {}
    private record Path(ConfigDefinition<?> definition, List<String> fields) {
        ConfigType<?> type() { ConfigType<?> type = definition.type(); for (String field : fields) type = type.fields().get(field).type(); return type; }
        LocalizedText name() {
            ConfigType<?> type = definition.type(); LocalizedText name = definition.name();
            for (String field : fields) { var metadata = type.fields().get(field); name = metadata.name(); type = metadata.type(); }
            return name;
        }
        String unit() {
            ConfigType<?> type = definition.type(); String unit = definition.unit();
            for (String field : fields) { var metadata = type.fields().get(field); unit = metadata.unit(); type = metadata.type(); }
            return unit;
        }
        Object extract(Object root) { Object value = root; for (String field : fields) value = ((Map<?, ?>) value).get(field); return value; }
        Object replace(Object root, Object value) { return definition.type().validate(replaceAt(root, value, 0)); }
        private Object replaceAt(Object root, Object value, int depth) {
            if (depth == fields.size()) return type().validate(value);
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) root).forEach((key, item) -> result.put((String) key, item));
            String field = fields.get(depth); result.put(field, replaceAt(result.get(field), value, depth + 1)); return result;
        }
    }
}
