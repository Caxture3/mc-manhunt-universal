package com.caxture.manhuntuniversal.presentation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ObserverPacketSnapshotTest {
    @Test void teamUpdatesRetainUncontrolledPlayersAndNeverChangeSharedInputPackets() {
        UUID target = UUID.randomUUID();
        var snapshot = new ObserverPacketSnapshot(Map.of(target, new ObserverPacketSnapshot.PlayerPatch("Alex", "mh1", "masked", false, 5)));
        List<String> original = new ArrayList<>(List.of("Alex", "Steve"));
        assertEquals(List.of("Steve"), snapshot.filterTeam("core_runners", original));
        assertEquals(List.of("Alex", "Steve"), original);
        assertEquals(List.of("Alex"), snapshot.filterTeam("mh1", List.of("Alex")));
        assertEquals(List.of("Alex", "Steve"), ObserverPacketSnapshot.empty().filterTeam("core_runners", original));
    }
    @Test void snapshotsAreIndependentAcrossObserversAndCannotBeMutatedAfterPublication() {
        UUID id = UUID.randomUUID(); Map<UUID, ObserverPacketSnapshot.PlayerPatch> values = new HashMap<>();
        values.put(id, new ObserverPacketSnapshot.PlayerPatch("Alex", "mh1", "red", true, 1));
        var first = new ObserverPacketSnapshot(values); values.clear();
        var second = new ObserverPacketSnapshot(Map.of(id, new ObserverPacketSnapshot.PlayerPatch("Alex", "mh2", "unknown", false, 9)));
        assertEquals("red", first.players().get(id).displayName()); assertEquals("unknown", second.players().get(id).displayName());
        assertThrows(UnsupportedOperationException.class, () -> first.players().clear());
    }
}
