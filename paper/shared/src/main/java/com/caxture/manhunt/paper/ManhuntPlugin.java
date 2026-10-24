package com.caxture.manhunt.paper;

import org.bukkit.plugin.java.JavaPlugin;

/** Minimal lifecycle entry point; gameplay will be migrated from the original project. */
public final class ManhuntPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("ManhuntUniversal skeleton enabled.");
    }
}

