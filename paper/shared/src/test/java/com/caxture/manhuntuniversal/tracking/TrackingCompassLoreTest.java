package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrackingCompassLoreTest {
    @Test void controlledCompassesLeaveGameplayInstructionsToTheProvider() {
        for (var kind : TrackingCompassKind.values()) {
            assertEquals(List.of("tracking.compass.lore-bound"), kind.loreKeys(true));
        }
        assertEquals(List.of("tracking.compass.lore-runner-use", "tracking.compass.lore-bound"),
                TrackingCompassKind.RUNNER.loreKeys(false));
        assertEquals(List.of("tracking.compass.lore-hunter-use", "tracking.compass.lore-bound"),
                TrackingCompassKind.HUNTER.loreKeys(false));
    }
}
