package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;

import java.util.Objects;
import java.util.function.Consumer;

/** Applies the visible side effects of a confirmed selector click in a stable order. */
public final class CompassSelectorCommit {
    private CompassSelectorCommit() {
    }

    public static void commit(TrackingSelectionTarget target,
                              Consumer<TrackingSelectionTarget> selectionHandler,
                              Runnable feedback,
                              Runnable closeScreen) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(selectionHandler, "selectionHandler").accept(target);
        Objects.requireNonNull(feedback, "feedback").run();
        Objects.requireNonNull(closeScreen, "closeScreen").run();
    }
}
