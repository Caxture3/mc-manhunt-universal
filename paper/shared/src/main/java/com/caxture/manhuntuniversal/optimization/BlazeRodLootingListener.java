package com.caxture.manhuntuniversal.optimization;

import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.config.IntConfigKey;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.SessionState;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.random.RandomGenerator;

/** Virtual minimum Looting for the vanilla blaze rod payload, without changing real equipment. */
public final class BlazeRodLootingListener implements Listener {
    private final BooleanSupplier running;
    private final IntSupplier configured;
    private final RandomGenerator random;
    private final LootAccess loot;

    public BlazeRodLootingListener(GameManager games, ConfigurationService configuration) {
        this(() -> games.activeSession().filter(session -> session.state() == SessionState.RUNNING).isPresent(),
                () -> configuration.get(IntConfigKey.BLAZE_ROD_LOOTING_LEVEL), RandomGenerator.getDefault(),
                new LootAccess() {
                    @Override public boolean allowed(Blaze entity) {
                        return Boolean.TRUE.equals(entity.getWorld().getGameRuleValue(GameRules.MOB_DROPS));
                    }
                    @Override public int looting(Player player) {
                        return player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
                    }
                    @Override public ItemStack rods(int count) { return new ItemStack(Material.BLAZE_ROD, count); }
                });
    }

    BlazeRodLootingListener(BooleanSupplier running, IntSupplier configured, RandomGenerator random, LootAccess loot) {
        this.running = running;
        this.configured = configured;
        this.random = random;
        this.loot = loot;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        int minimum = configured.getAsInt();
        if (event.isCancelled() || minimum == 0 || !running.getAsBoolean()
                || !(event.getEntity() instanceof Blaze blaze)
                || !(event.getDamageSource().getCausingEntity() instanceof Player player)
                || blaze.getKiller() == null || !loot.allowed(blaze)) return;
        int existing = Math.max(0, loot.looting(player));
        int level = BlazeRodDrops.upgradedLevel(minimum, existing);
        if (level == 0 || !BlazeRodDrops.canReplace(event.getDrops(), existing)) return;
        int count = BlazeRodDrops.count(random.nextInt(2), random.nextFloat(), level);
        // Let Paper commit the final drops only if the death remains uncancelled.
        BlazeRodDrops.replace(event.getDrops(), count, loot::rods);
    }

    /** Boundary for live server registries; tests exercise real events with deterministic native inputs. */
    interface LootAccess {
        boolean allowed(Blaze entity);
        int looting(Player player);
        ItemStack rods(int count);
    }
}
