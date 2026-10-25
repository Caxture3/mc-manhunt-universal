package com.caxture.manhuntuniversal.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Main-thread lobby choices; insertion order determines which runner reservations expire first. */
public final class TeamSelectionService {
    private final BooleanSupplier locked;
    private final Map<UUID, TeamPreference> choices = new LinkedHashMap<>();
    private Consumer<UUID> choiceChanged = playerId -> {};
    private boolean forcedRandom;

    public TeamSelectionService(BooleanSupplier locked) {
        this.locked = Objects.requireNonNull(locked);
    }

    public void onChoiceChanged(Consumer<UUID> listener) {
        choiceChanged = Objects.requireNonNull(listener);
    }

    public ChoiceResult choose(UUID playerId, TeamPreference preference, int runnerCapacity) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(preference);
        if (forcedRandom) return ChoiceResult.DISABLED;
        if (locked.getAsBoolean()) return ChoiceResult.LOCKED;
        if (preferenceOf(playerId) == preference) return ChoiceResult.UNCHANGED;
        if (preference == TeamPreference.RUNNER
                && choices.values().stream().filter(value -> value == TeamPreference.RUNNER).count() >= runnerCapacity) {
            return ChoiceResult.FULL;
        }
        choices.remove(playerId);
        if (preference != TeamPreference.RANDOM) choices.put(playerId, preference);
        choiceChanged.accept(playerId);
        return ChoiceResult.CHANGED;
    }

    public TeamPreference preferenceOf(UUID playerId) {
        return choices.getOrDefault(playerId, TeamPreference.RANDOM);
    }

    public Map<UUID, TeamPreference> snapshot() {
        return Map.copyOf(choices);
    }

    public boolean forcedRandom() {
        return forcedRandom;
    }

    public void setForcedRandom(boolean enabled) {
        forcedRandom = enabled;
        if (enabled) clear();
    }

    /** Returns removed reservations in latest-first order; it never changes an active session. */
    public List<UUID> trimRunners(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative runner capacity");
        if (locked.getAsBoolean()) return List.of();
        List<UUID> runners = choices.entrySet().stream()
                .filter(entry -> entry.getValue() == TeamPreference.RUNNER)
                .map(Map.Entry::getKey).toList();
        List<UUID> removed = runners.subList(Math.min(capacity, runners.size()), runners.size()).reversed();
        removed.forEach(this::remove);
        return List.copyOf(removed);
    }

    public void remove(UUID playerId) {
        if (choices.remove(playerId) != null) choiceChanged.accept(playerId);
    }

    public void clear() {
        List<UUID> removed = List.copyOf(choices.keySet());
        choices.clear();
        removed.forEach(choiceChanged);
    }

    public enum ChoiceResult { CHANGED, UNCHANGED, FULL, LOCKED, DISABLED }
}
