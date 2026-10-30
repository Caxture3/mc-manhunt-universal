package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/** Mutable state shared by the cached callbacks of one authorized dialog window. */
final class TrackingDialogWindow<A> {
    // Paper retains callbacks for five minutes. Rotate early while reusing actions between refreshes.
    private static final long ACTION_REFRESH_NANOS = Duration.ofMinutes(4).toNanos();

    private final Consumer<TrackingTargetId> selectionHandler;
    private final Runnable dismissHandler;
    private TrackingTargetId currentSelection;
    private TrackingAvailability availability;
    private boolean active = true;
    private A actions;
    private long actionRevision;
    private long actionsCreatedAt;

    TrackingDialogWindow(TrackingTargetId currentSelection, TrackingAvailability availability,
                         Consumer<TrackingTargetId> selectionHandler, Runnable dismissHandler) {
        this.currentSelection = Objects.requireNonNull(currentSelection, "currentSelection");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.selectionHandler = Objects.requireNonNull(selectionHandler, "selectionHandler");
        this.dismissHandler = Objects.requireNonNull(dismissHandler, "dismissHandler");
    }

    boolean update(TrackingTargetId currentSelection, TrackingAvailability availability) {
        if (!active) return false;
        this.currentSelection = Objects.requireNonNull(currentSelection, "currentSelection");
        this.availability = Objects.requireNonNull(availability, "availability");
        return true;
    }

    A actions(long nowNanos, LongFunction<A> factory) {
        if (actions == null || nowNanos - actionsCreatedAt >= ACTION_REFRESH_NANOS) {
            actionsCreatedAt = nowNanos;
            actions = factory.apply(++actionRevision);
        }
        return actions;
    }

    void select(long revision, TrackingTargetId target) {
        if (!active || revision != actionRevision
                || !availability.unlocked(TrackingSelectionTargetMapper.toProtocol(target))) return;
        active = false;
        selectionHandler.accept(currentSelection == target ? TrackingTargetId.NONE : target);
    }

    void dismiss(long revision) {
        // An Esc sent while a refresh is in flight still belongs to this same authorized window.
        if (!active || revision < 1 || revision > actionRevision) return;
        active = false;
        dismissHandler.run();
    }

    void close() { active = false; }

    boolean active() { return active; }

    TrackingTargetId currentSelection() { return currentSelection; }

    TrackingAvailability availability() { return availability; }
}
