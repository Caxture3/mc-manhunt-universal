package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Before an atomic role assignment; cancellation leaves the assignment unchanged. Always fired synchronously on the server main thread. */
public final class RolesChangingEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot before;
    private final Map<UUID, TeamRole> proposed;
    private final ChangeSource source;
    private boolean cancelled;

    /**
     * Creates an event with detached immutable match data.
     * @param before detached snapshot before the role change
     * @param proposed candidate result or complete role assignment
     * @param source origin and explanation of the role change
     */
    public RolesChangingEvent(MatchSnapshot before, Map<UUID, TeamRole> proposed, ChangeSource source) {
        this.before = Objects.requireNonNull(before, "before");
        this.proposed = Map.copyOf(proposed);
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Returns the before supplied to this event.
     * @return the before
     */
    public MatchSnapshot getBefore() { return before; }

    /**
     * Returns the proposed supplied to this event.
     * @return the proposed
     */
    public Map<UUID, TeamRole> getProposed() { return proposed; }

    /**
     * Returns the source supplied to this event.
     * @return the source
     */
    public ChangeSource getSource() { return source; }

    /**
     * Returns whether this pending operation has been cancelled.
     * @return the requested value or operation outcome
     */
    @Override public boolean isCancelled() { return cancelled; }
    /**
     * Sets cancellation of this pending operation.
     * @param cancelled whether the pending operation is cancelled
     */
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /**
     * Returns this concrete event type's handler list.
     * @return the handlers
     */
    @Override public HandlerList getHandlers() { return HANDLERS; }
    /**
     * Returns this concrete event type's handler list.
     * @return the handlerList
     */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
