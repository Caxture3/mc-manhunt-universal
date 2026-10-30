package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public interface TrackingSelectionPresenter {
    void show(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId currentSelection,
            TrackingAvailability availability,
            Consumer<TrackingTargetId> selectionHandler,
            Runnable dismiss
    );

    void update(Player player, UUID sessionId, long authorizationId,
                TrackingTargetId currentSelection, TrackingAvailability availability);

    void close(Player player, UUID sessionId, long authorizationId);

    void invalidate(UUID playerId);
}
