package com.caxture.manhuntuniversal.listener;

import com.caxture.manhuntuniversal.runtime.ActiveGameCoordinator;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.command.TeamSelectionPresenter;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class GameEventListener implements Listener {
    private final ActiveGameCoordinator coordinator;
    private final GameManager gameManager;
    private final TeamSelectionPresenter teams;

    public GameEventListener(ActiveGameCoordinator coordinator, GameManager gameManager, TeamSelectionPresenter teams) {
        this.coordinator = coordinator;
        this.gameManager = gameManager;
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        coordinator.playerDied(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            coordinator.enderDragonDied(dragon.getWorld().getUID());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        coordinator.playerChangedWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        coordinator.playerTeleported(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        coordinator.playerTeleported(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        coordinator.playerJoined(event.getPlayer());
        if (gameManager.activeSession().isEmpty()) teams.show(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        coordinator.playerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        coordinator.playerRespawned(event.getPlayer());
    }
}
