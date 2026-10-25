package com.caxture.manhuntuniversal.tracking;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public interface TrackingSelectionPresenter {
    void show(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId currentSelection,
            Consumer<TrackingTargetId> selectionHandler
    );

    void close(Player player, UUID sessionId, long authorizationId);

    void invalidate(UUID playerId);
}
