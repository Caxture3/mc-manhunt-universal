package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortalInvalidationBufferTest {
    @Test void repeatedChangesWaitTwoTicksAfterTheLatestEventForEachColumn() {
        var buffer = new PortalInvalidationBuffer();
        UUID world = UUID.randomUUID();
        buffer.mark(world, 0, 0, 10);
        assertTrue(buffer.drain(11).isEmpty());
        buffer.mark(world, 0, 0, 11);
        assertTrue(buffer.drain(12).isEmpty());
        assertEquals(Set.of(new PortalSearchCursor.Chunk(0, 0)), buffer.drain(13).get(world));
        assertTrue(buffer.drain(14).isEmpty());
    }

    @Test void unrelatedColumnsAndWorldsDoNotPostponeEachOtherAndUnloadDropsPendingWork() {
        var buffer = new PortalInvalidationBuffer();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        buffer.mark(first, -1, -2, 0);
        buffer.mark(first, 8, 9, 1);
        buffer.mark(second, 0, 0, 1);
        assertEquals(Set.of(new PortalSearchCursor.Chunk(-1, -2)), buffer.drain(2).get(first));
        buffer.removeWorld(second);
        var ready = buffer.drain(3);
        assertFalse(ready.containsKey(second));
        assertEquals(Set.of(new PortalSearchCursor.Chunk(8, 9)), ready.get(first));
        buffer.mark(first, 1, 1, 3);
        buffer.clear();
        assertTrue(buffer.drain(99).isEmpty());
    }
}
