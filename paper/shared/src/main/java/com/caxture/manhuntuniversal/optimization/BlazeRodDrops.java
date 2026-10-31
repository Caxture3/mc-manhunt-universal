package com.caxture.manhuntuniversal.optimization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.function.IntFunction;

/** Both supported vanilla blaze tables use integer 0/1 plus rounded float * Looting level. */
final class BlazeRodDrops {
    private BlazeRodDrops() {}

    static int upgradedLevel(int configured, int existing) { return configured > existing ? configured : 0; }
    static int count(int base, float roll, int level) { return base + Math.round(roll * level); }

    private static boolean ordinaryRod(ItemStack item) {
        return item.getType() == Material.BLAZE_ROD && !item.hasItemMeta();
    }

    static boolean canReplace(List<ItemStack> drops, int existingLooting) {
        int stacks = 0;
        for (ItemStack item : drops) {
            if (ordinaryRod(item) && (++stacks > 1 || item.getAmount() > existingLooting + 1)) return false;
        }
        return true;
    }

    static void replace(List<ItemStack> drops, int count, IntFunction<ItemStack> createRod) {
        drops.removeIf(BlazeRodDrops::ordinaryRod);
        if (count > 0) drops.add(createRod.apply(count));
    }
}
