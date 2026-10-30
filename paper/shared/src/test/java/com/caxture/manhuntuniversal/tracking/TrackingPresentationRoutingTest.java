package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.client.TrackingClientPublisher;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TrackingPresentationRoutingTest {
    private final UUID playerId = new UUID(0, 1);
    private final UUID sessionId = new UUID(0, 2);
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUniqueId")) return playerId;
                throw new UnsupportedOperationException(method.getName());
            });
    private final ClientRoute client = new ClientRoute();
    private final PaperRoute paper = new PaperRoute();
    private final TrackingSelectionPresenter router = new FabricAwareTrackingSelectionPresenter(client, paper);

    @Test
    void updateRequiresTheActiveAuthorizationAndKeepsTheOriginalRoute() {
        client.compatible = true;
        router.show(player, sessionId, 5, TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> {});
        client.compatible = false;
        router.update(player, sessionId, 4, TrackingTargetId.NONE, TrackingAvailability.allUnlocked());
        router.update(player, new UUID(0, 3), 5, TrackingTargetId.NONE, TrackingAvailability.allUnlocked());
        assertEquals(0, client.updates);

        TrackingAvailability locked = new TrackingAvailability(8_000, -1, 0);
        router.update(player, sessionId, 5, TrackingTargetId.VILLAGE, locked);
        assertEquals(1, client.updates);
        assertEquals(locked, client.availability);
        assertEquals(TrackingSelectionTarget.VILLAGE, client.selection);
        assertEquals(0, paper.updates);
        router.close(player, sessionId, 5);
        router.update(player, sessionId, 5, TrackingTargetId.NONE, locked);
        assertEquals(1, client.updates);
    }

    @Test
    void paperDismissStopsUpdatesAndAnOlderDismissCannotCloseTheNewWindow() {
        int[] dismissals = {0};
        router.show(player, sessionId, 5, TrackingTargetId.VILLAGE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> dismissals[0]++);
        Runnable staleDismiss = paper.dismiss;
        router.show(player, sessionId, 6, TrackingTargetId.VILLAGE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> dismissals[0]++);
        staleDismiss.run();
        router.update(player, sessionId, 6, TrackingTargetId.VILLAGE, TrackingAvailability.allUnlocked());
        assertEquals(1, paper.updates);
        assertEquals(0, dismissals[0]);

        paper.dismiss.run();
        router.update(player, sessionId, 6, TrackingTargetId.VILLAGE, TrackingAvailability.allUnlocked());
        assertEquals(1, paper.updates);
        assertEquals(1, dismissals[0]);
    }

    @Test
    void paperDismissSendsAnOrderedCloseBeforeFinishingCancellation() {
        int[] dismissals = {0};
        router.show(player, sessionId, 5, TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> {
                    assertEquals(1, paper.closes, "Close must follow any already-sent refresh packet");
                    dismissals[0]++;
                });
        paper.dismiss.run();
        assertEquals(1, dismissals[0]);
        assertEquals(1, paper.closes);
        router.close(player, sessionId, 5);
        assertEquals(1, paper.closes);
        router.update(player, sessionId, 5, TrackingTargetId.NONE, TrackingAvailability.allUnlocked());
        assertEquals(0, paper.updates);
    }

    @Test
    void invalidationAlsoInvalidatesFallbackCallbacks() {
        router.show(player, sessionId, 5, TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> fail());
        router.invalidate(playerId);
        assertEquals(playerId, paper.invalidated);
        router.update(player, sessionId, 5, TrackingTargetId.NONE, TrackingAvailability.allUnlocked());
        assertEquals(0, paper.updates);
        paper.dismiss.run();
    }

    @Test
    void replacedWindowRejectsTheOldDismissEvenWhenItsAuthorizationIsUnchanged() {
        router.show(player, sessionId, 5, TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> fail());
        Runnable previous = paper.dismiss;
        router.show(player, sessionId, 5, TrackingTargetId.NONE,
                TrackingAvailability.allUnlocked(), ignored -> {}, () -> {});
        previous.run();
        router.update(player, sessionId, 5, TrackingTargetId.NONE, TrackingAvailability.allUnlocked());
        assertEquals(1, paper.updates);
    }

    private static final class ClientRoute implements TrackingClientPublisher {
        boolean compatible;
        int updates;
        TrackingAvailability availability;
        TrackingSelectionTarget selection;

        public boolean supportsCompatibleClient(UUID id) { return compatible; }
        public void openTrackingSelection(Player p, UUID id, long auth, TrackingSelectionTarget selection,
                                          TrackingAvailability availability) {}
        public void updateTrackingSelection(Player p, UUID id, long auth, TrackingSelectionTarget selection,
                                            TrackingAvailability availability) {
            updates++;
            this.selection = selection;
            this.availability = availability;
        }
        public void closeTrackingSelection(Player p, UUID id, long auth) {}
    }

    private static final class PaperRoute implements TrackingSelectionPresenter {
        int updates;
        int closes;
        Runnable dismiss;
        UUID invalidated;

        public void show(Player p, UUID id, long auth, TrackingTargetId selection,
                         TrackingAvailability availability, Consumer<TrackingTargetId> handler, Runnable dismiss) {
            this.dismiss = dismiss;
        }
        public void update(Player p, UUID id, long auth, TrackingTargetId selection,
                           TrackingAvailability availability) { updates++; }
        public void close(Player p, UUID id, long auth) { closes++; }
        public void invalidate(UUID id) { invalidated = id; }
    }
}
