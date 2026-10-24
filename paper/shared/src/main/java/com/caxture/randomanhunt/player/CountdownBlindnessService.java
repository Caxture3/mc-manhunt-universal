package com.caxture.randomanhunt.player;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.UUID;

public final class CountdownBlindnessService {
    private static final int COVERAGE_TICKS = 40;

    private final Server server;
    private final PendingPlayerState pendingState;

    public CountdownBlindnessService(Server server, PendingPlayerState pendingState) {
        this.server = server;
        this.pendingState = pendingState;
    }

    public void apply(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingState.blindnessToRestore(playerId).isEmpty()) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.BLINDNESS);
            pendingState.rememberBlindness(playerId, Optional.ofNullable(current).map(BlindnessSnapshot::from));
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, COVERAGE_TICKS, 0, false, false, false));
    }

    public void restore(Player player) {
        Optional<Optional<BlindnessSnapshot>> debt = pendingState.blindnessToRestore(player.getUniqueId());
        if (debt.isEmpty()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        debt.orElseThrow().ifPresent(snapshot -> player.addPotionEffect(snapshot.toPotionEffect()));
        pendingState.completeBlindnessRestore(player.getUniqueId());
    }

    public void restoreAllOnline() {
        for (UUID playerId : pendingState.playersWithBlindnessDebt()) {
            Player player = server.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restore(player);
            }
        }
    }

    public void discard(UUID playerId) {
        pendingState.completeBlindnessRestore(playerId);
    }
}
