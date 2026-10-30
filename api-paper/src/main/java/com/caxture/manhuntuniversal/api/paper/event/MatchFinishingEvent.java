package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import java.util.Objects;

/** Before victory is committed; listeners may replace the result or cancel settlement. Always fired synchronously on the server main thread. */
public final class MatchFinishingEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private Victory victory;
    private boolean cancelled;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param victory candidate result
     */
    public MatchFinishingEvent(MatchSnapshot match, Victory victory) {
        this.match = Objects.requireNonNull(match, "match");
        this.victory = Objects.requireNonNull(victory, "victory");
    }

    /**
     * Returns the match supplied to this event.
     * @return the match
     */
    public MatchSnapshot getMatch() { return match; }

    /**
     * Returns the victory supplied to this event.
     * @return the victory
     */
    public Victory getVictory() { return victory; }

    /**
     * Replaces the candidate result. Null is rejected.
     * @param victory replacement result
     */
    public void setVictory(Victory victory) { this.victory = Objects.requireNonNull(victory, "victory"); }

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
