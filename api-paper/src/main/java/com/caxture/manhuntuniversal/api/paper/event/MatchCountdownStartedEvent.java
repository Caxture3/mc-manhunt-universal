package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;

/** The accepted match has begun counting down. Always fired synchronously on the server main thread. */
public final class MatchCountdownStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final int countdownSeconds;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param countdownSeconds non-negative countdown duration in seconds
     */
    public MatchCountdownStartedEvent(MatchSnapshot match, int countdownSeconds) {
        this.match = Objects.requireNonNull(match, "match");
        if (countdownSeconds < 0) throw new IllegalArgumentException("countdownSeconds must be non-negative");
        this.countdownSeconds = countdownSeconds;
    }

    /**
     * Returns the match supplied to this event.
     * @return the match
     */
    public MatchSnapshot getMatch() { return match; }

    /**
     * Returns the countdownSeconds supplied to this event.
     * @return the countdownSeconds
     */
    public int getCountdownSeconds() { return countdownSeconds; }

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
