package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SharedSearchCacheTest {
    private long now;
    private final List<UUID> cancelled = new ArrayList<>();
    private final SharedSearchCache<String, Integer> cache =
            new SharedSearchCache<>(2, 100, 10, () -> now, cancelled::add);

    @Test void sameInputSharesWorkAndCancellingOwnerKeepsOtherSubscriber() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        List<Optional<Integer>> received = new ArrayList<>();
        var work = cache.request(first, "same", ignored -> fail("cancelled subscriber"));
        assertNotNull(work);
        assertNull(cache.request(second, "same", received::add));
        cache.cancel(first);
        assertTrue(work.active());
        assertTrue(cancelled.isEmpty());
        work.complete(Optional.of(42));
        assertEquals(List.of(Optional.of(42)), received);
        assertFalse(work.active());
        assertNull(cache.request(first, "same", received::add));
        assertEquals(List.of(Optional.of(42), Optional.of(42)), received);
    }

    @Test void cancelledWorkCannotCompleteOverReplacementOrPopulateCache() {
        UUID player = UUID.randomUUID();
        List<Optional<Integer>> received = new ArrayList<>();
        var old = cache.request(player, "old", received::add);
        var replacement = cache.request(player, "new", received::add);
        assertFalse(old.active());
        old.complete(Optional.of(1));
        assertTrue(received.isEmpty());
        replacement.complete(Optional.of(2));
        assertEquals(List.of(Optional.of(2)), received);
        assertNotNull(cache.request(player, "old", received::add));
    }

    @Test void positiveAndNegativeTtlAndLruAreBounded() {
        UUID player = UUID.randomUUID();
        cache.request(player, "a", ignored -> {}).complete(Optional.of(1));
        cache.request(player, "b", ignored -> {}).complete(Optional.empty());
        now = 9;
        assertNull(cache.request(player, "b", ignored -> {}));
        now = 10;
        var negativeRefresh = cache.request(player, "b", ignored -> {});
        assertNotNull(negativeRefresh);
        negativeRefresh.complete(Optional.empty());
        assertNull(cache.request(player, "a", ignored -> {})); // a becomes most recently used
        cache.request(player, "c", ignored -> {}).complete(Optional.of(3));
        assertEquals(2, cache.cachedCount());
        assertNotNull(cache.request(player, "b", ignored -> {}));
        cache.cancel(player);
        now = 100;
        assertNotNull(cache.request(player, "a", ignored -> {}));
    }

    @Test void clearAndInvalidationDropInFlightAndLateResults() {
        UUID player = UUID.randomUUID();
        var old = cache.request(player, "world-a", ignored -> fail("stale"));
        cache.invalidateMatching(key -> key.equals("world-a"));
        assertFalse(old.active());
        old.complete(Optional.of(1));
        assertEquals(0, cache.cachedCount());
        var fresh = cache.request(player, "world-a", ignored -> fail("cleared"));
        cache.clear();
        fresh.complete(Optional.of(2));
        assertFalse(fresh.active());
        assertEquals(0, cache.cachedCount());
    }

    @Test void replacingCallbackForSameRequestDoesNotRestartWorkAndFailureIsNotCached() {
        UUID player = UUID.randomUUID();
        List<Optional<Integer>> received = new ArrayList<>();
        var work = cache.request(player, "same", ignored -> fail("replaced callback"));
        assertNull(cache.request(player, "same", received::add));
        assertTrue(work.active());
        work.fail();
        assertEquals(List.of(Optional.empty()), received);
        assertNotNull(cache.request(player, "same", received::add));
    }
}
