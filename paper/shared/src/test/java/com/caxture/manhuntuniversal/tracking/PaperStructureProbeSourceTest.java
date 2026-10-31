package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class PaperStructureProbeSourceTest {
    private final UUID id = UUID.randomUUID();
    private final Set<Integer> loaded = new HashSet<>(Set.of(0));
    private final Map<PortalPoint, Block> blocks = new HashMap<>();
    private int reads;
    private final World world = proxy(World.class, (method, args) -> switch (method) {
        case "getMinHeight" -> 0;
        case "getMaxHeight" -> 256;
        case "isChunkLoaded" -> loaded.contains((int) args[0]);
        case "getBlockAt" -> {
            var point = new PortalPoint((int) args[0], (int) args[1], (int) args[2]);
            assertTrue(loaded.contains(point.x() >> 4), "Validation cannot load a chunk");
            reads++;
            yield blocks.get(point);
        }
        default -> throw new AssertionError("Unexpected world access: " + method);
    });
    private final Server server = proxy(Server.class, (method, args) -> {
        assertEquals("getWorld", method);
        return id.equals(args[0]) ? world : null;
    });
    private final PaperStructureProbeSource source = new PaperStructureProbeSource(server);

    @Test void onlyActualBlazeSpawnersAreAcceptedAndSnapshotsDoNotLoadBlocks() {
        var blaze = new PortalPoint(1, 70, 1);
        var zombie = new PortalPoint(2, 70, 1);
        var destroyed = new PortalPoint(3, 70, 1);
        blocks.put(blaze, block(Material.SPAWNER, EntityType.BLAZE));
        blocks.put(zombie, block(Material.SPAWNER, EntityType.ZOMBIE));
        blocks.put(destroyed, block(Material.AIR, null));
        assertEquals(List.of(blaze), source.spawners(id, List.of(blaze, zombie, destroyed)));
        assertEquals(3, reads);
        blocks.put(blaze, block(Material.AIR, null));
        assertTrue(source.spawners(id, List.of(blaze)).isEmpty());
    }

    @Test void unloadedChunksAndInvalidHeightNeverAccessBlocks() {
        assertTrue(source.spawners(id, List.of(new PortalPoint(48, 70, 1),
                new PortalPoint(1, -1, 1), new PortalPoint(1, 256, 1))).isEmpty());
        assertEquals(0, reads);
    }

    @Test void unknownWorldAndEmptyInputDoNotReadBlocks() {
        assertTrue(source.spawners(UUID.randomUUID(), List.of(new PortalPoint(1, 70, 1))).isEmpty());
        assertTrue(source.spawners(id, List.of()).isEmpty());
        assertEquals(0, reads);
    }

    private static Block block(Material material, EntityType entity) {
        CreatureSpawner state = proxy(CreatureSpawner.class, (method, args) -> {
            assertEquals("getSpawnedType", method);
            return entity;
        });
        return proxy(Block.class, (method, args) -> switch (method) {
            case "getType" -> material;
            case "getState" -> { assertArrayEquals(new Object[]{false}, args); yield state; }
            default -> throw new AssertionError(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(instance);
                        case "equals" -> instance == args[0];
                        case "toString" -> type.getSimpleName();
                        default -> throw new AssertionError(method);
                    };
                    return handler.apply(method.getName(), args);
                });
    }
}
