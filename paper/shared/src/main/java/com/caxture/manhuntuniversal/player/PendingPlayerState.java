package com.caxture.manhuntuniversal.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PendingPlayerState {
    private final Set<UUID> pendingResets = new HashSet<>();
    private final Map<UUID, Optional<BlindnessSnapshot>> originalBlindness = new HashMap<>();

    public void markResetPending(UUID playerId) {
        pendingResets.add(playerId);
    }

    public boolean needsReset(UUID playerId) {
        return pendingResets.contains(playerId);
    }

    public void completeReset(UUID playerId) {
        pendingResets.remove(playerId);
    }

    public void rememberBlindness(UUID playerId, Optional<BlindnessSnapshot> snapshot) {
        originalBlindness.putIfAbsent(playerId, snapshot);
    }

    public Optional<Optional<BlindnessSnapshot>> blindnessToRestore(UUID playerId) {
        return Optional.ofNullable(originalBlindness.get(playerId));
    }

    public Set<UUID> playersWithBlindnessDebt() {
        return Set.copyOf(originalBlindness.keySet());
    }

    public void completeBlindnessRestore(UUID playerId) {
        originalBlindness.remove(playerId);
    }
}
