package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;
import java.util.UUID;

/** A participant has been revived through standard restoration. Always fired synchronously on the server main thread. */
public final class ParticipantRevivedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final UUID playerId;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param playerId affected participant identifier
     */
    public ParticipantRevivedEvent(MatchSnapshot match, UUID playerId) {
        this.match = Objects.requireNonNull(match, "match");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
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
