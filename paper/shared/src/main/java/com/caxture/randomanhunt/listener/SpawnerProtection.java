package com.caxture.randomanhunt.listener;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public final class SpawnerProtection {
    private SpawnerProtection() {
    }

    public static boolean isProtected(Material blockType, EntityType spawnedType, boolean running, boolean allowBreaking) {
        return running && !allowBreaking && blockType == Material.SPAWNER && spawnedType == EntityType.BLAZE;
    }
}
