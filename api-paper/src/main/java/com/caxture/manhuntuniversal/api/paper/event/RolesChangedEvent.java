package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;

/** An atomic role assignment and dependent presentation updates have completed. Always fired synchronously on the server main thread. */
public final class RolesChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot before;
    private final MatchSnapshot after;
    private final ChangeSource source;

    /**
     * Creates an event with detached immutable match data.
     * @param before detached snapshot before the role change
     * @param after detached snapshot after the role change
     * @param source origin and explanation of the role change
     */
    public RolesChangedEvent(MatchSnapshot before, MatchSnapshot after, ChangeSource source) {
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Returns the before supplied to this event.
     * @return the before
     */
    public MatchSnapshot getBefore() { return before; }

    /**
     * Returns the after supplied to this event.
     * @return the after
     */
    public MatchSnapshot getAfter() { return after; }

    /**
     * Returns the source supplied to this event.
     * @return the source
     */
    public ChangeSource getSource() { return source; }

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
