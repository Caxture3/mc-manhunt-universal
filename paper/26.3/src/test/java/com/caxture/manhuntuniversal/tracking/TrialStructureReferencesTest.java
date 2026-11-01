package com.caxture.manhuntuniversal.tracking;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrialStructureReferencesTest {
    @Test void matchesOnlyMinecraftTrialReferences() {
        assertTrue(StructureChunkReferences.matches(Key.key("minecraft","trial_chambers"),TrackingTargetId.TRIAL_CHAMBERS));
        assertFalse(StructureChunkReferences.matches(Key.key("minecraft","fortress"),TrackingTargetId.TRIAL_CHAMBERS));
        assertFalse(StructureChunkReferences.matches(Key.key("test","trial_chambers"),TrackingTargetId.TRIAL_CHAMBERS));
    }
}
