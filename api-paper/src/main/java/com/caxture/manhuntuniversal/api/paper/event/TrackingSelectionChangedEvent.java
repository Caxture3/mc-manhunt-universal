package com.caxture.manhuntuniversal.api.paper.event;
import com.caxture.manhuntuniversal.api.tracking.TrackingChange;
import org.bukkit.event.*;
import java.util.Objects;
public final class TrackingSelectionChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TrackingChange change;
    public TrackingSelectionChangedEvent(TrackingChange change) { this.change = Objects.requireNonNull(change); }
    public TrackingChange getChange() { return change; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
