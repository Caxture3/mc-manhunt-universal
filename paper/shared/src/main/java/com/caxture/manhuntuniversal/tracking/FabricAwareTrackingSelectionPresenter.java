package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.client.TrackingClientPublisher;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Routes compatible clients to Fabric while retaining the Paper Dialog fallback. */
public final class FabricAwareTrackingSelectionPresenter implements TrackingSelectionPresenter {
    private final TrackingClientPublisher clients;
    private final TrackingSelectionPresenter fallback;
    private final Map<UUID, ActivePresentation> activePresentations = new HashMap<>();

    public FabricAwareTrackingSelectionPresenter(
            TrackingClientPublisher clients,
            TrackingSelectionPresenter fallback
    ) {
        this.clients = Objects.requireNonNull(clients, "clients");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public void show(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId currentSelection,
            TrackingAvailability availability,
            Consumer<TrackingTargetId> selectionHandler,
            Runnable dismiss
    ) {
        UUID playerId = player.getUniqueId();
        ActivePresentation previous = activePresentations.remove(playerId);
        if (previous != null) {
            closeRoute(player, previous);
        }
        PresentationRoute route = clients.supportsCompatibleClient(playerId)
                ? PresentationRoute.FABRIC : PresentationRoute.PAPER;
        ActivePresentation presentation = new ActivePresentation(sessionId, authorizationId, route);
        activePresentations.put(playerId, presentation);
        if (route == PresentationRoute.FABRIC) {
            clients.openTrackingSelection(
                    player,
                    sessionId,
                    authorizationId,
                    TrackingSelectionTargetMapper.toProtocol(currentSelection), availability);
            return;
        }
        fallback.show(
                player, sessionId, authorizationId, currentSelection, availability,
                target -> {
                    if (activePresentations.get(playerId) == presentation) selectionHandler.accept(target);
                },
                () -> {
                    if (activePresentations.get(playerId) == presentation) {
                        activePresentations.remove(playerId);
                        // Send an ordered close after any refresh already in flight before releasing the capability.
                        closeRoute(player, presentation);
                        dismiss.run();
                    }
                });
    }

    @Override
    public void update(Player player, UUID sessionId, long authorizationId,
                       TrackingTargetId currentSelection, TrackingAvailability availability) {
        ActivePresentation current = activePresentations.get(player.getUniqueId());
        if (current == null || !current.matches(sessionId, authorizationId)) return;
        if (current.route() == PresentationRoute.FABRIC) {
            clients.updateTrackingSelection(player, sessionId, authorizationId,
                    TrackingSelectionTargetMapper.toProtocol(currentSelection), availability);
        } else {
            fallback.update(player, sessionId, authorizationId, currentSelection, availability);
        }
    }

    @Override
    public void close(Player player, UUID sessionId, long authorizationId) {
        ActivePresentation current = activePresentations.get(player.getUniqueId());
        if (current == null || !current.matches(sessionId, authorizationId)) {
            return;
        }
        activePresentations.remove(player.getUniqueId());
        closeRoute(player, current);
    }

    @Override
    public void invalidate(UUID playerId) {
        activePresentations.remove(playerId);
        fallback.invalidate(playerId);
    }

    private void closeRoute(Player player, ActivePresentation presentation) {
        if (presentation.route() == PresentationRoute.FABRIC) {
            clients.closeTrackingSelection(
                    player, presentation.sessionId(), presentation.authorizationId());
        } else {
            fallback.close(
                    player, presentation.sessionId(), presentation.authorizationId());
        }
    }

    private enum PresentationRoute {
        FABRIC,
        PAPER
    }

    private record ActivePresentation(
            UUID sessionId,
            long authorizationId,
            PresentationRoute route
    ) {
        private boolean matches(UUID expectedSessionId, long expectedAuthorizationId) {
            return sessionId.equals(expectedSessionId)
                    && authorizationId == expectedAuthorizationId;
        }
    }
}
