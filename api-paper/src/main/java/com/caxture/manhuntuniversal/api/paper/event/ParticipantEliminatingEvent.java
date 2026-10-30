package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import java.util.Objects;
import java.util.UUID;

/** Before standard participant elimination; cancellation blocks elimination. Always fired synchronously on the server main thread. */
public final class ParticipantEliminatingEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final UUID playerId;
    private final String cause;
    private boolean cancelled;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param playerId affected participant identifier
     * @param cause elimination trigger; API for explicit requests
     */
    public ParticipantEliminatingEvent(MatchSnapshot match, UUID playerId, String cause) {
        this.match = Objects.requireNonNull(match, "match");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /**
     * Returns the match supplied to this event.
     * @return the match
     */
    public MatchSnapshot getMatch() { return match; }

    /**
     * Returns the playerId supplied to this event.
     * @return the playerId
     */
    public UUID getPlayerId() { return playerId; }

    /**
     * Returns the cause supplied to this event.
     * @return the cause
     */
    public String getCause() { return cause; }

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
