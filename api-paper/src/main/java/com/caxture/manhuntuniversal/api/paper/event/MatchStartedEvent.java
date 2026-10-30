package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;

/** The match has entered RUNNING after standard start processing. Always fired synchronously on the server main thread. */
public final class MatchStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     */
    public MatchStartedEvent(MatchSnapshot match) {
        this.match = Objects.requireNonNull(match, "match");
    }

    /**
     * Returns the match supplied to this event.
     * @return the match
     */
    public MatchSnapshot getMatch() { return match; }

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
