package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;

import java.util.Objects;
import java.util.Optional;

/** Pure input mapping for the selector's full-panel buttons. */
public final class CompassSelectorInteraction {
    // Minecraft 1.21.11's GLFW-backed MouseButtonInfo uses 0 for the primary mouse button.
    private static final int PRIMARY_MOUSE_BUTTON = 0;

    private CompassSelectorInteraction() {
    }

    public static Optional<TrackingSelectionTarget> selectionAt(
            CompassSelectorLayout layout,
            int mouseX,
            int mouseY,
            int mouseButton,
            TrackingSelectionTarget currentSelection) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(currentSelection, "currentSelection");
        if (mouseButton != PRIMARY_MOUSE_BUTTON) {
            return Optional.empty();
        }
        return layout.panelAt(mouseX, mouseY)
                .stream()
                .mapToObj(index -> CompassSelectorOption.values()[index].target())
                .map(clicked -> clicked == currentSelection ? TrackingSelectionTarget.NONE : clicked)
                .findFirst();
    }
}
