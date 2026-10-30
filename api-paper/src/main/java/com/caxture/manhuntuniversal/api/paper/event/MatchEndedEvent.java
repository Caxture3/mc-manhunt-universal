package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;
import java.util.Optional;

/** A match ended after complete cleanup, including stop, cancellation, and disable paths. Always fired synchronously on the server main thread. */
public final class MatchEndedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final String reason;
    private final Optional<Victory> victory;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param reason explanation of the change or result
     * @param victory candidate result
     */
    public MatchEndedEvent(MatchSnapshot match, String reason, Optional<Victory> victory) {
        this.match = Objects.requireNonNull(match, "match");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.victory = Objects.requireNonNull(victory, "victory");
    }

    /**
     * Returns the match supplied to this event.
     * @return the match
     */
    public MatchSnapshot getMatch() { return match; }

    /**
     * Returns the reason supplied to this event.
     * @return the reason
     */
    public String getReason() { return reason; }

    /**
     * Returns the victory supplied to this event.
     * @return the victory
     */
    public Optional<Victory> getVictory() { return victory; }

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
