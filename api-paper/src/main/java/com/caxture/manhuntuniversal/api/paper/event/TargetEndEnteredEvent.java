package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;
import java.util.UUID;

/** A living runner has entered the target End world. Always fired synchronously on the server main thread. */
public final class TargetEndEnteredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MatchSnapshot match;
    private final UUID playerId;
    private final UUID worldId;

    /**
     * Creates an event with detached immutable match data.
     * @param match detached immutable match snapshot
     * @param playerId affected participant identifier
     * @param worldId target End world identifier
     */
    public TargetEndEnteredEvent(MatchSnapshot match, UUID playerId, UUID worldId) {
        this.match = Objects.requireNonNull(match, "match");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.worldId = Objects.requireNonNull(worldId, "worldId");
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
     * Returns the worldId supplied to this event.
     * @return the worldId
     */
    public UUID getWorldId() { return worldId; }

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
