package com.caxture.randomanhunt.listener;

import com.caxture.randomanhunt.i18n.LanguageService;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlazeSpawnerProtectionListener implements Listener {
    private final LanguageService language;

    public BlazeSpawnerProtectionListener(LanguageService language) {
        this.language = language;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawner)
                || !SpawnerProtection.isProtected(event.getBlock().getType(), spawner.getSpawnedType())) {
            return;
        }
        event.setCancelled(true);
        language.send(event.getPlayer(), "restriction.blaze-spawner-break-denied");
    }
}
