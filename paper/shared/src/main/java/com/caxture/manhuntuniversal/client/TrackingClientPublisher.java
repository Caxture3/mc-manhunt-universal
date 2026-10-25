package com.caxture.manhuntuniversal.client;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface TrackingClientPublisher {
    boolean supportsCompatibleClient(UUID playerId);

    void openTrackingSelection(Player player, UUID sessionId, long authorizationId,
                               TrackingSelectionTarget currentSelection);

    void closeTrackingSelection(Player player, UUID sessionId, long authorizationId);
}
