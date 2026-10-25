package com.caxture.randomanhunt.optimization;

import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.config.IntConfigKey;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.SessionState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Supplies an independent Fortune level to native ore loot without changing held tools. */
public final class OreFortuneListener implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final Map<BreakKey, ItemStack> pendingTools = new HashMap<>();

    public OreFortuneListener(JavaPlugin plugin, GameManager gameManager, ConfigurationService configuration) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.configuration = configuration;
    }

    @SuppressWarnings("removal") // isPreferredTool is not equivalent to vanilla drop eligibility.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        int configured = configuration.get(IntConfigKey.ORE_FORTUNE_LEVEL);
        if (!running() || configured == 0 || !event.isDropItems()
                || player.getGameMode() == GameMode.CREATIVE
                || OreFortuneDrops.oreDrop(block.getType()) == null) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        int level = OreFortuneDrops.upgradedLevel(configured,
                held.getEnchantmentLevel(Enchantment.FORTUNE), held.containsEnchantment(Enchantment.SILK_TOUCH));
        if (level == 0 || !block.isValidTool(held)) return;

        // BlockDropItemEvent runs after tool damage: the original tool may have broken.
        ItemStack virtualTool = held.clone();
        virtualTool.addUnsafeEnchantment(Enchantment.FORTUNE, level);
        BreakKey key = BreakKey.of(player, block);
        pendingTools.put(key, virtualTool);
        // Cancelled/no-drop breaks never emit BlockDropItemEvent; expire their snapshots.
        plugin.getServer().getScheduler().runTask(plugin, () -> pendingTools.remove(key, virtualTool));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDrop(BlockDropItemEvent event) {
        ItemStack virtualTool = pendingTools.remove(BreakKey.of(event.getPlayer(), event.getBlock()));
        if (virtualTool == null || event.isCancelled() || !running()) return;
        BlockState original = event.getBlockState();
        Material oreDrop = OreFortuneDrops.oreDrop(original.getType());
        if (oreDrop == null) return;
        OreFortuneDrops.replace(event, oreDrop, original.getDrops(virtualTool, event.getPlayer()));
    }

    private boolean running() {
        return gameManager.activeSession().filter(session -> session.state() == SessionState.RUNNING).isPresent();
    }

    private record BreakKey(UUID player, UUID world, int x, int y, int z) {
        private static BreakKey of(Player player, Block block) {
            return new BreakKey(player.getUniqueId(), block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ());
        }
    }
}
