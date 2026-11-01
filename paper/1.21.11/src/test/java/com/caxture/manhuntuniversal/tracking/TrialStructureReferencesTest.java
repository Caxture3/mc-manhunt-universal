package com.caxture.manhuntuniversal.tracking;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrialStructureReferencesTest {
    @Test void matchesOnlyMinecraftTrialReferences() {
        assertTrue(StructureChunkReferences.matches(NamespacedKey.minecraft("trial_chambers"),TrackingTargetId.TRIAL_CHAMBERS));
        assertFalse(StructureChunkReferences.matches(NamespacedKey.minecraft("fortress"),TrackingTargetId.TRIAL_CHAMBERS));
        assertFalse(StructureChunkReferences.matches(new NamespacedKey("test","trial_chambers"),TrackingTargetId.TRIAL_CHAMBERS));
    }
}
