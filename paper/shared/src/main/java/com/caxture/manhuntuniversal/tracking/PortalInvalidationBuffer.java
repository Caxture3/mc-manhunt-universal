package com.caxture.manhuntuniversal.tracking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-column debounce; a later change cannot be flushed using an earlier event's deadline. */
final class PortalInvalidationBuffer {
    private final Map<UUID, Map<PortalSearchCursor.Chunk, Long>> pending = new HashMap<>();

    void mark(UUID world, int chunkX, int chunkZ, long cycle) {
        pending.computeIfAbsent(world, ignored -> new HashMap<>())
                .put(new PortalSearchCursor.Chunk(chunkX, chunkZ), cycle + 2);
    }

    Map<UUID, Set<PortalSearchCursor.Chunk>> drain(long cycle) {
        Map<UUID, Set<PortalSearchCursor.Chunk>> ready = new HashMap<>();
        var worlds = pending.entrySet().iterator();
        while (worlds.hasNext()) {
            var world = worlds.next();
            var columns = world.getValue().entrySet().iterator();
            while (columns.hasNext()) {
                var column = columns.next();
                if (column.getValue() <= cycle) {
                    ready.computeIfAbsent(world.getKey(), ignored -> new HashSet<>()).add(column.getKey());
                    columns.remove();
                }
            }
            if (world.getValue().isEmpty()) worlds.remove();
        }
        return ready;
    }

    void removeWorld(UUID world) { pending.remove(world); }
    void clear() { pending.clear(); }
}
