package com.caxture.manhuntuniversal.api.paper.event;
import com.caxture.manhuntuniversal.api.tracking.*;
import org.bukkit.event.*;
import java.util.*;
public final class TrackingTargetChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TrackingChange change;
    private final Optional<TrackingSnapshot> snapshot;
    public TrackingTargetChangedEvent(TrackingChange change, Optional<TrackingSnapshot> snapshot) { this.change = Objects.requireNonNull(change); this.snapshot = Objects.requireNonNull(snapshot); }
    public TrackingChange getChange() { return change; }
    public Optional<TrackingSnapshot> getSnapshot() { return snapshot; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
