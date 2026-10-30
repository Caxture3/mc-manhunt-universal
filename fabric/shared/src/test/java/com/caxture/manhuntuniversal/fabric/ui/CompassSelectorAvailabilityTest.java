package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompassSelectorAvailabilityTest {
    @Test
    void hiddenOptionsLeaveEqualPanelsInOriginalOrder() {
        var layout = CompassSelectorLayout.fit(1600, 900, new TrackingAvailability(0, -1, 5000));
        assertEquals(2, layout.panelCount());
        assertEquals(new SelectorRect(0, 0, 800, 900), layout.panel(0));
        assertEquals(new SelectorRect(800, 0, 800, 900), layout.panel(1));
        assertEquals(CompassSelectorOption.VILLAGE, layout.option(0));
        assertEquals(CompassSelectorOption.BASTION_REMNANT, layout.option(1));
        assertEquals(1, layout.panelAt(800, 450).orElseThrow());
    }

    @Test
    void aSinglePanelFillsCanvasAndHiddenAllCannotBeHit() {
        var single = CompassSelectorLayout.fit(1600, 900, new TrackingAvailability(-1, 0, -1));
        assertEquals(new SelectorRect(0, 0, 1600, 900), single.panel(0));
        assertEquals(CompassSelectorOption.NETHER_FORTRESS, single.option(0));
        var hidden = CompassSelectorLayout.fit(1600, 900, new TrackingAvailability(-1, -1, -1));
        assertTrue(hidden.panelAt(500, 500).isEmpty());
    }

    @Test
    void threePanelsKeepTheirEdgesWithoutDeadPixels() {
        var layout = CompassSelectorLayout.fit(1600, 900, TrackingAvailability.allUnlocked());
        assertEquals(533, layout.panel(0).right());
        assertEquals(0, layout.panelAt(532, 450).orElseThrow());
        assertEquals(1, layout.panelAt(533, 450).orElseThrow());
        assertEquals(2, layout.panelAt(1066, 450).orElseThrow());
        assertTrue(layout.panelAt(1600, 450).isEmpty());
    }

    @Test
    void lockedHoverNeverBrightensOrSelectsAndUnlockedCurrentTogglesOff() {
        var availability = new TrackingAvailability(-1, 2000, 0);
        var layout = CompassSelectorLayout.fit(1600, 900, availability);
        int primary = Integer.parseInt(System.getProperty("selector.primaryMouseButton"));
        assertTrue(CompassSelectorInteraction.selectionAt(layout, 100, 450, primary,
                TrackingSelectionTarget.NONE, availability).isEmpty());
        assertEquals(TrackingSelectionTarget.NONE, CompassSelectorInteraction.selectionAt(
                layout, 900, 450, primary, TrackingSelectionTarget.BASTION_REMNANT, availability).orElseThrow());
        assertEquals(0.5F, CompassSelectorLayout.opacity(0, 0, 0, false));
        assertEquals(1.0F, CompassSelectorLayout.opacity(0, -1, 0, true));
    }

    @Test
    void remainingTimeUsesMonotonicElapsedAndUpdatesReplaceTheAnchor() {
        var clock = new TrackingAvailabilityClock(new TrackingAvailability(-1, 2000, 0), 9_000_000_000L);
        assertEquals(new TrackingAvailability(-1, 1500, 0), clock.at(9_500_000_000L));
        assertEquals(new TrackingAvailability(-1, 0, 0), clock.at(12_000_000_000L));
        clock.update(new TrackingAvailability(0, 4000, -1), 12_000_000_000L);
        assertEquals(new TrackingAvailability(0, 3500, -1), clock.at(12_500_000_000L));
    }
}
