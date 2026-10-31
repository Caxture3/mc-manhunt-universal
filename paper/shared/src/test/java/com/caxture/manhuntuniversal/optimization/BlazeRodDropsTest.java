package com.caxture.manhuntuniversal.optimization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BlazeRodDropsTest {
    @Test void virtualLevelNeverAddsToOrDowngradesExistingLooting() {
        assertEquals(0, BlazeRodDrops.upgradedLevel(0, 0));
        assertEquals(5, BlazeRodDrops.upgradedLevel(5, 3));
        assertEquals(0, BlazeRodDrops.upgradedLevel(3, 3));
        assertEquals(0, BlazeRodDrops.upgradedLevel(5, 8));
    }
    @Test void distributionUsesRoundedContinuousLootingBonusAndRetainsZeroDropPossibility() {
        for (int level = 1; level <= 5; level++) {
            assertEquals(0, BlazeRodDrops.count(0, 0, level));
            assertEquals(level + 1, BlazeRodDrops.count(1, Math.nextDown(1f), level));
            double sum = 0;
            int zero = 0;
            for (int base = 0; base <= 1; base++) for (int i = 0; i < 1000; i++) {
                int result = BlazeRodDrops.count(base, (i + .5f) / 1000, level);
                assertTrue(result >= 0 && result <= level + 1);
                sum += result;
                if (result == 0) zero++;
            }
            assertEquals((level + 1) / 2.0, sum / 2000, .002);
            assertEquals(1.0 / (4 * level), zero / 2000.0, .001);
        }
    }
    @Test void replacementKeepsOtherItemsAndMetadataAndCanUpgradeAnOriginallyEmptyRoll() {
        var secondary = stack(Material.DIAMOND, 2, false);
        var namedRod = stack(Material.BLAZE_ROD, 1, true);
        var ordinary = stack(Material.BLAZE_ROD, 1, false);
        List<ItemStack> drops = new ArrayList<>(List.of(ordinary, secondary, namedRod));
        assertTrue(BlazeRodDrops.canReplace(drops, 0));
        BlazeRodDrops.replace(drops, 6, amount -> stack(Material.BLAZE_ROD, amount, false));
        assertEquals(3, drops.size());
        assertSame(secondary, drops.get(0));
        assertSame(namedRod, drops.get(1));
        assertEquals(6, drops.get(2).getAmount());
        assertEquals(1, ordinary.getAmount(), "Do not mutate original snapshots");
        drops.clear();
        BlazeRodDrops.replace(drops, 2, amount -> stack(Material.BLAZE_ROD, amount, false));
        assertEquals(2, drops.getFirst().getAmount());
        BlazeRodDrops.replace(drops, 0, amount -> fail("zero count must not create a stack"));
        assertTrue(drops.isEmpty());
    }
    @Test void obviouslyCustomizedOrdinaryRodPayloadIsLeftToItsProducer() {
        assertFalse(BlazeRodDrops.canReplace(List.of(stack(Material.BLAZE_ROD, 20, false)), 0));
        assertFalse(BlazeRodDrops.canReplace(List.of(stack(Material.BLAZE_ROD, 1, false),
                stack(Material.BLAZE_ROD, 1, false)), 0));
    }
    static ItemStack stack(Material type, int amount, boolean metadata) { return new Stack(type, amount, metadata); }
    private static final class Stack extends ItemStack {
        private final Material type;
        private final int amount;
        private final boolean metadata;
        Stack(Material type, int amount, boolean metadata) { this.type = type; this.amount = amount; this.metadata = metadata; }
        @Override public Material getType() { return type; }
        @Override public int getAmount() { return amount; }
        @Override public boolean hasItemMeta() { return metadata; }
    }
}
