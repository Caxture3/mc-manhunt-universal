package com.caxture.manhuntuniversal.extension.text;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.text.*;
import com.caxture.manhuntuniversal.api.paper.text.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.function.Function;

public final class TextRegistryService implements PaperTextRegistry {
    private final OwnedRegistry owned;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, LocalizedText> definitions = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> rendering = new HashSet<>();
    public TextRegistryService(OwnedRegistry owned) { this.owned = owned; }
    @Override public ExtensionResult<ExtensionHandle> register(ExtensionRegistration owner, String key, LocalizedText definition) {
        return owned.execute(owner, () -> {
            if (key == null || !key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || definition == null)
                return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, "Namespaced text key required");
            if (definitions.containsKey(key)) return conflict();
            definitions.put(key, definition);
            return ExtensionResult.ok(owned.add(owner, () -> definitions.remove(key, definition)));
        });
    }
    @Override public ExtensionResult<ExtensionHandle> override(ExtensionRegistration owner, String key, int priority, TextProvider provider) {
        return add(owner, key, priority, provider, null);
    }
    @Override public ExtensionResult<ExtensionHandle> component(ExtensionRegistration owner, String key, int priority, Function<PaperTextContext, Component> provider) {
        return add(owner, key, priority, null, provider);
    }
    private ExtensionResult<ExtensionHandle> add(ExtensionRegistration owner, String key, int priority,
                                                TextProvider text, Function<PaperTextContext, Component> component) {
        return owned.execute(owner, () -> {
            if (key == null || key.isBlank() || text == null && component == null) return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, "Key/provider required");
            if (entries.stream().anyMatch(entry -> entry.key.equals(key) && entry.priority == priority)) return conflict();
            Entry entry = new Entry(owner, key, priority, text, component); entries.add(entry);
            return ExtensionResult.ok(owned.add(owner, () -> entries.remove(entry)));
        });
    }
    @Override public Optional<String> resolve(TextContext context) {
        return resolve(context,true);
    }
    public Optional<String> resolveOverride(TextContext context) { return resolve(context,false); }
    public Optional<LocalizedText> definition(String key) { owned.requireThread(); return Optional.ofNullable(definitions.get(key)); }
    private Optional<String> resolve(TextContext context, boolean includeDefinition) {
        owned.requireThread();
        if (!resolving.add(context.key())) return Optional.empty();
        try {
            for (Entry entry : matching(context.key())) if (entry.text != null) {
                try { String result = owned.invoke(() -> entry.text.provide(context)); if (result != null) return Optional.of(result); }
                catch (RuntimeException | LinkageError error) { failed(entry, error); }
            }
            return includeDefinition ? Optional.ofNullable(definitions.get(context.key())).map(value -> value.resolve(context.locale())) : Optional.empty();
        } finally { resolving.remove(context.key()); }
    }
    public Component component(PaperTextContext context) {
        owned.requireThread();
        if (!rendering.add(context.text().key())) return context.fallback();
        try {
        for (Entry entry : matching(context.text().key())) {
            if (entry.text != null) return context.fallback();
            if (entry.component != null) try {
                Component result = owned.invoke(() -> entry.component.apply(context)); if (result != null) return result;
            } catch (RuntimeException | LinkageError error) { failed(entry, error); }
        }
        return context.fallback();
        } finally { rendering.remove(context.text().key()); }
    }
    private List<Entry> matching(String key) { return entries.stream().filter(value -> owned.valid(value.owner) && value.key.equals(key)).sorted(Comparator.comparingInt((Entry value) -> value.priority).reversed()).toList(); }
    private void failed(Entry entry, Throwable error) { owned.report(new IllegalStateException("Text provider failed: " + entry.owner.id() + "/" + entry.key, error)); }
    private static <T> ExtensionResult<T> conflict() { return ExtensionResult.rejected(OperationStatus.CONTROL_CONFLICT, "Text key/priority already registered"); }
    private record Entry(ExtensionRegistration owner, String key, int priority, TextProvider text, Function<PaperTextContext, Component> component) { }
}
