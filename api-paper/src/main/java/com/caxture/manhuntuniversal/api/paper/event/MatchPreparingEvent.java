package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import java.util.Objects;

/** Before a match is accepted; cancellation rejects start. Always fired synchronously on the server main thread. */
public final class MatchPreparingEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final int countdownSeconds;
    private boolean cancelled;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param countdownSeconds non-negative countdown duration in seconds
     */
    public MatchPreparingEvent(MatchSnapshot match, int countdownSeconds) {
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
