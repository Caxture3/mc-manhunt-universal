package com.caxture.manhuntuniversal.client;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface TrackingClientPublisher {
    default boolean openDynamic(Player player, com.caxture.manhuntuniversal.protocol.DynamicTrackingOpen message) { return false; }
    default boolean updateDynamic(Player player, com.caxture.manhuntuniversal.protocol.DynamicTrackingUpdate message) { return false; }
    boolean supportsCompatibleClient(UUID playerId);

    void openTrackingSelection(Player player, UUID sessionId, long authorizationId,
                               TrackingSelectionTarget currentSelection, TrackingAvailability availability);

    void updateTrackingSelection(Player player, UUID sessionId, long authorizationId,
                                 TrackingSelectionTarget currentSelection, TrackingAvailability availability);

    void closeTrackingSelection(Player player, UUID sessionId, long authorizationId);
}
