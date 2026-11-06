package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;

import java.util.Objects;
import java.util.Optional;

/** Pure input mapping for the selector's full-panel buttons. */
public final class CompassSelectorInteraction {
    // Minecraft 26.3's MouseButtonInfo uses 1 for the primary mouse button.
    private static final int PRIMARY_MOUSE_BUTTON = 1;

    private CompassSelectorInteraction() {
    }

    public static boolean isPrimaryButton(int mouseButton) {
        return mouseButton == PRIMARY_MOUSE_BUTTON;
    }

    public static Optional<com.caxture.manhuntuniversal.protocol.TrackingTargetDescriptor> selectionAt(
            SelectorPageState state, SelectorRect canvas, int mouseX, int mouseY,
            int mouseButton, long now) {
        if (!isPrimaryButton(mouseButton)) return Optional.empty();
        return state.hit(canvas, mouseX, mouseY).stream()
                .mapToObj(index -> state.options().get(index))
                .filter(option -> state.enabled(option, now)).findFirst();
    }

    public static Optional<TrackingSelectionTarget> selectionAt(
            CompassSelectorLayout layout,
            int mouseX,
            int mouseY,
            int mouseButton,
            TrackingSelectionTarget currentSelection) {
        return selectionAt(layout, mouseX, mouseY, mouseButton, currentSelection, TrackingAvailability.allUnlocked());
    }

    public static Optional<TrackingSelectionTarget> selectionAt(
            CompassSelectorLayout layout, int mouseX, int mouseY, int mouseButton,
            TrackingSelectionTarget currentSelection, TrackingAvailability availability) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(currentSelection, "currentSelection");
        if (!isPrimaryButton(mouseButton)) {
            return Optional.empty();
        }
        return layout.panelAt(mouseX, mouseY)
                .stream()
                .mapToObj(index -> layout.option(index).target())
                .filter(availability::unlocked)
                .map(clicked -> clicked == currentSelection ? TrackingSelectionTarget.NONE : clicked)
                .findFirst();
    }
}
