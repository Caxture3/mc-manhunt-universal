package com.caxture.manhuntuniversal.optimization;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

class PiglinBarterPolicyTest {
    @Test
    void everyPromotionTicketProducesTheTargetRatesWhileScalingAllOtherResultsEqually() {
        var counts = new EnumMap<PiglinBarterPolicy.Replacement, Integer>(PiglinBarterPolicy.Replacement.class);
        for (int roll = 0; roll < 154752; roll++) counts.merge(PiglinBarterPolicy.replacement(roll), 1, Integer::sum);
        assertEquals(11168, counts.get(PiglinBarterPolicy.Replacement.PEARLS));
        assertEquals(12780, counts.get(PiglinBarterPolicy.Replacement.OBSIDIAN));
        assertEquals(1149, counts.get(PiglinBarterPolicy.Replacement.FIRE_RESISTANCE));
        assertEquals(1149, counts.get(PiglinBarterPolicy.Replacement.SPLASH_FIRE_RESISTANCE));
        assertEquals(128506, counts.get(PiglinBarterPolicy.Replacement.KEEP));
        double pearlRate = 10.0 / 469 + 403.0 / 469 * 11168 / 154752;
        double obsidianRate = 40.0 / 469 + 403.0 / 469 * 12780 / 154752;
        double potionRate = 8.0 / 469 + 403.0 / 469 * 1149 / 154752;
        assertEquals(16, 64 * pearlRate * 3, 1e-12);
        assertEquals(10, 64 * obsidianRate, 1e-12);
        assertEquals(3, 64 * potionRate * 2, 1e-12);
        assertEquals(137.0 / 192, 403.0 / 469 * 128506 / 154752, 1e-12);
    }

    @Test
    void promotionTicketBoundsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> PiglinBarterPolicy.replacement(-1));
        assertThrows(IllegalArgumentException.class, () -> PiglinBarterPolicy.replacement(154752));
    }
}
