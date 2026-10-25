package com.caxture.randomanhunt.optimization;

import com.caxture.randomanhunt.config.BooleanConfigKey;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.i18n.LanguageService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class HunterEndAccessListener implements Listener {
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final LanguageService language;

    public HunterEndAccessListener(GameManager gameManager, ConfigurationService configuration, LanguageService language) {
        this.gameManager = gameManager;
        this.configuration = configuration;
        this.language = language;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.END_PORTAL_FRAME
                || event.getItem() == null || event.getItem().getType() != Material.ENDER_EYE
                || !blocked(event.getPlayer())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        language.send(event.getPlayer(), "restriction.hunter-end-locked");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { blockEntry(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { blockEntry(event); }

    private void blockEntry(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld().getEnvironment() != World.Environment.THE_END
                || event.getTo().getWorld().equals(event.getFrom().getWorld())
                || !blocked(event.getPlayer())) return;
        event.setCancelled(true);
        language.send(event.getPlayer(), "restriction.hunter-end-locked");
    }

    private boolean blocked(Player player) {
        return HunterEndAccessPolicy.blocked(gameManager.activeSession().orElse(null), player.getUniqueId(),
                configuration.get(BooleanConfigKey.HUNTER_END_RESTRICTION_ENABLED));
    }
}
