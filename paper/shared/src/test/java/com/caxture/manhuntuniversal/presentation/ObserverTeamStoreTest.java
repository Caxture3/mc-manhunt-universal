package com.caxture.manhuntuniversal.presentation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObserverTeamStoreTest {
    @Test void removingTheLastProviderCannotCloseTheMirrorBeforeFailedRestoresAreRetried() throws Exception {
        var store = new ObserverTeamStore<String, String>();
        store.put("pair", "synthetic-team"); store.send("pair", (value, first) -> {});
        List<String> lifecycle = new ArrayList<>();
        Runnable closeMirror = () -> { lifecycle.add("close"); store.forget(key -> true); };
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThrows(ReflectiveOperationException.class, () -> store.restore("pair", value -> {
                throw new ReflectiveOperationException("remove failed");
            }, value -> fail("cannot restore membership")));
            assertFalse(store.closeIfRestored(closeMirror));
            assertEquals("synthetic-team", store.get("pair"));
        }
        assertTrue(lifecycle.isEmpty());
        store.restore("pair", value -> lifecycle.add("remove"), value -> lifecycle.add("membership"));
        assertTrue(store.closeIfRestored(closeMirror));
        assertEquals(List.of("remove", "membership", "close"), lifecycle);
    }
    @Test void failedFirstCreateIsRetriedAsCreateAndSuccessfulCreateThenUsesModify() throws Exception {
        var store = new ObserverTeamStore<String, String>();
        store.put("pair", "synthetic-team");
        List<Boolean> modes = new ArrayList<>();
        assertThrows(ReflectiveOperationException.class, () -> store.send("pair", (value, first) -> {
            modes.add(first); throw new ReflectiveOperationException("send failed");
        }));
        assertEquals("synthetic-team", store.get("pair"));
        store.send("pair", (value, first) -> modes.add(first));
        store.send("pair", (value, first) -> modes.add(first));
        assertEquals(List.of(true, true, false), modes);
    }
    @Test void failedRemovalRetainsTeamAndFailedMembershipRetainsOnlyPendingRestore() throws Exception {
        var store = new ObserverTeamStore<String, String>();
        store.put("pair", "synthetic-team"); store.send("pair", (value, first) -> {});
        List<String> sends = new ArrayList<>();
        assertThrows(ReflectiveOperationException.class, () -> store.restore("pair", value -> {
            sends.add("remove-failed"); throw new ReflectiveOperationException();
        }, value -> fail("must not commit")));
        assertEquals("synthetic-team", store.get("pair"));
        assertThrows(ReflectiveOperationException.class, () -> store.restore("pair", value -> sends.add("remove"), value -> {
            sends.add("membership-failed"); throw new ReflectiveOperationException();
        }));
        assertEquals("synthetic-team", store.get("pair"));
        store.restore("pair", value -> fail("already removed"), value -> sends.add("membership"));
        assertNull(store.get("pair"));
        assertEquals(List.of("remove-failed", "remove", "membership-failed", "membership"), sends);
    }
}
