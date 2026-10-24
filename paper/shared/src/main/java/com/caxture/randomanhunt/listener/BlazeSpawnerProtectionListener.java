package com.caxture.randomanhunt.listener;

import com.caxture.randomanhunt.i18n.LanguageService;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.SessionState;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.config.BooleanConfigKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlazeSpawnerProtectionListener implements Listener {
    private final LanguageService language;
    private final GameManager gameManager;
    private final ConfigurationService configuration;

    public BlazeSpawnerProtectionListener(LanguageService language, GameManager gameManager, ConfigurationService configuration) {
        this.language = language;
        this.gameManager = gameManager;
        this.configuration = configuration;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        boolean running = gameManager.activeSession().filter(session -> session.state() == SessionState.RUNNING).isPresent();
        boolean allowed = configuration.get(BooleanConfigKey.ALLOW_BLAZE_SPAWNER_BREAK);
        if (!running || allowed) return;
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawner)
                || !SpawnerProtection.isProtected(event.getBlock().getType(), spawner.getSpawnedType(), running, allowed)) {
            return;
        }
        event.setCancelled(true);
        language.send(event.getPlayer(), "restriction.blaze-spawner-break-denied");
    }
}
