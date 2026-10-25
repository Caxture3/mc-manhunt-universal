package com.caxture.randomanhunt.optimization;

import com.caxture.randomanhunt.config.BooleanConfigKey;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.SessionState;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.random.RandomGenerator;

public final class PiglinBarterListener implements Listener {
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final RandomGenerator random;

    public PiglinBarterListener(GameManager gameManager, ConfigurationService configuration) {
        this(gameManager, configuration, RandomGenerator.getDefault());
    }

    PiglinBarterListener(GameManager gameManager, ConfigurationService configuration, RandomGenerator random) {
        this.gameManager = gameManager;
        this.configuration = configuration;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent event) {
        if (!configuration.get(BooleanConfigKey.ADJUST_PIGLIN_BARTERING)
                || gameManager.activeSession().filter(s -> s.state() == SessionState.RUNNING).isEmpty()
                || event.getInput().getType() != Material.GOLD_INGOT
                || event.getOutcome().size() != 1 || isTarget(event.getOutcome().getFirst())) return;
        PiglinBarterPolicy.Replacement replacement = PiglinBarterPolicy.replacement(
                random.nextInt(PiglinBarterPolicy.ROLL_BOUND));
        if (replacement != PiglinBarterPolicy.Replacement.KEEP) {
            event.getOutcome().set(0, createReplacement(replacement));
        }
    }

    static boolean isTarget(ItemStack item) {
        return item.getType() == Material.ENDER_PEARL || item.getType() == Material.OBSIDIAN
                || ((item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION)
                && item.getItemMeta() instanceof PotionMeta potion
                && potion.getBasePotionType() == PotionType.FIRE_RESISTANCE);
    }

    private ItemStack createReplacement(PiglinBarterPolicy.Replacement replacement) {
        return switch (replacement) {
            case PEARLS -> new ItemStack(Material.ENDER_PEARL, random.nextInt(2, 5));
            case OBSIDIAN -> new ItemStack(Material.OBSIDIAN);
            case FIRE_RESISTANCE, SPLASH_FIRE_RESISTANCE -> {
                ItemStack item = new ItemStack(replacement == PiglinBarterPolicy.Replacement.FIRE_RESISTANCE
                        ? Material.POTION : Material.SPLASH_POTION);
                PotionMeta potion = (PotionMeta) item.getItemMeta();
                potion.setBasePotionType(PotionType.FIRE_RESISTANCE);
                item.setItemMeta(potion);
                yield item;
            }
            case KEEP -> throw new IllegalArgumentException("KEEP has no replacement item");
        };
    }
}
