package com.caxture.manhuntuniversal.extension.feature;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.feature.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import java.util.*;

/** One active provider per effect; no event mutation or uncancellation is performed here. */
public final class FeatureRegistryService implements FeatureRegistry {
    private final OwnedRegistry owned;
    private final List<Entry> entries = new ArrayList<>();
    public FeatureRegistryService(OwnedRegistry owned) { this.owned = owned; }
    @Override public Set<String> list() { owned.requireThread(); return CoreFeatureIds.all(); }
    @Override public ExtensionResult<ExtensionHandle> policy(ExtensionRegistration owner, String id, int priority, FeaturePolicy policy) {
        return owned.execute(owner, () -> {
            if (!CoreFeatureIds.all().contains(id) || policy == null) return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, "Unknown feature or null policy");
            if (entries.stream().anyMatch(entry -> entry.id.equals(id) && entry.priority == priority))
                return ExtensionResult.rejected(OperationStatus.CONTROL_CONFLICT, "Equal-priority feature policy");
            Entry entry = new Entry(owner, id, priority, policy); entries.add(entry);
            return ExtensionResult.ok(owned.add(owner, () -> entries.remove(entry)));
        });
    }
    public boolean allows(FeatureContext context) {
        owned.requireThread();
        Entry entry = entries.stream().filter(value -> owned.valid(value.owner) && value.id.equals(context.featureId()))
                .max(Comparator.comparingInt(value -> value.priority)).orElse(null);
        if (entry == null) return true;
        try { return owned.invoke(() -> entry.policy.decide(context)) != FeatureDecision.SKIP_CORE; }
        catch (RuntimeException | LinkageError error) { owned.report(new IllegalStateException("Feature policy failed: " + entry.owner.id(), error)); return true; }
    }
    private record Entry(ExtensionRegistration owner, String id, int priority, FeaturePolicy policy) { }
}
