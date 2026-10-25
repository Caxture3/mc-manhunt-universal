package com.caxture.randomanhunt.optimization;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class OreFortuneDrops {
    private OreFortuneDrops() {}

    static int upgradedLevel(int configured, int existing, boolean silkTouch) {
        return !silkTouch && configured > existing ? configured : 0;
    }

    static Material oreDrop(Material block) {
        return switch (block) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.COAL;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.RAW_IRON;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.EMERALD;
            case NETHER_GOLD_ORE -> Material.GOLD_NUGGET;
            case NETHER_QUARTZ_ORE -> Material.QUARTZ;
            case ANCIENT_DEBRIS -> Material.ANCIENT_DEBRIS;
            default -> null;
        };
    }

    static void replace(BlockDropItemEvent event, Material oreDrop, Collection<ItemStack> nativeDrops) {
        if (event.isCancelled()) return;
        List<Item> items = event.getItems();
        Item original = items.stream().filter(item -> item.getItemStack().getType() == oreDrop)
                .findFirst().orElse(null);
        // Respect plugins which already suppressed/replaced the ore's drops.
        if (original == null) return;

        Location location = original.getLocation();
        List<Item> replacements = new ArrayList<>();
        for (ItemStack drop : nativeDrops) {
            // Only reroll the ore payload; secondary/plugin drops already exist in the event.
            if (drop.getType() != oreDrop) continue;
            int remaining = drop.getAmount();
            int maximum = Math.max(1, drop.getMaxStackSize());
            while (remaining > 0) {
                ItemStack split = drop.clone();
                split.setAmount(Math.min(remaining, maximum));
                // Never spawn during this cancellable event. Paper spawns its final list
                // only after all handlers have run and the event remains uncancelled.
                Item replacement = event.getBlockState().getWorld().createEntity(location, Item.class);
                replacement.setItemStack(split);
                replacements.add(replacement);
                remaining -= split.getAmount();
            }
        }
        // A torch or other attached-block drop belongs to the same event: retain it.
        items.removeIf(item -> item.getItemStack().getType() == oreDrop);
        items.addAll(replacements);
    }
}
