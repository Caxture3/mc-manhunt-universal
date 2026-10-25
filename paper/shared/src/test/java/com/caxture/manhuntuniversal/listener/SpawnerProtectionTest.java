package com.caxture.manhuntuniversal.listener;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnerProtectionTest {
    @Test
    void onlyRunningGamesWithBreakingDisabledProtectBlazeSpawners() {
        assertFalse(SpawnerProtection.isProtected(Material.SPAWNER, EntityType.BLAZE, false, false));
        assertFalse(SpawnerProtection.isProtected(Material.SPAWNER, EntityType.BLAZE, false, true));
        assertFalse(SpawnerProtection.isProtected(Material.SPAWNER, EntityType.BLAZE, true, true));
        assertTrue(SpawnerProtection.isProtected(Material.SPAWNER, EntityType.BLAZE, true, false));
        assertFalse(SpawnerProtection.isProtected(Material.SPAWNER, EntityType.ZOMBIE, true, false));
        assertFalse(SpawnerProtection.isProtected(Material.TRIAL_SPAWNER, EntityType.BLAZE, true, false));
    }
}
