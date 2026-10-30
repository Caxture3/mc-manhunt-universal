package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PortalTrackingListenerTest {
    private final List<List<Integer>> queries = new ArrayList<>();
    private boolean neighbouringChunkLoaded = true;
    private boolean adjacentPortal;
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "isChunkLoaded" -> (int) args[0] == 0 && (int) args[1] == 0 || neighbouringChunkLoaded;
                case "getBlockAt" -> {
                    int x = (int) args[0], y = (int) args[1], z = (int) args[2];
                    assertTrue(x >> 4 == 0 && z >> 4 == 0 || neighbouringChunkLoaded,
                            "must not force-load a neighbour for invalidation");
                    queries.add(List.of(x, y, z));
                    yield block(x, y, z, adjacentPortal && x == 9 ? Material.NETHER_PORTAL : Material.AIR);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });

    @Test void ordinaryObsidianMiningDoesNotInvalidateEveryPortalSearch() {
        assertFalse(PortalTrackingListener.isPortalOrFrame(block(8, 64, 8, Material.OBSIDIAN)));
        assertEquals(6, queries.size());
        assertFalse(PortalTrackingListener.isPortalOrFrame(block(8, 64, 8, Material.STONE)));
    }

    @Test void actualFrameAndPortalInvalidateWhileUnknownNeighbourNeverLoadsAChunk() {
        adjacentPortal = true;
        assertTrue(PortalTrackingListener.isPortalOrFrame(block(8, 64, 8, Material.OBSIDIAN)));
        assertTrue(PortalTrackingListener.isPortalOrFrame(block(8, 64, 8, Material.NETHER_PORTAL)));
        adjacentPortal = false;
        neighbouringChunkLoaded = false;
        assertTrue(PortalTrackingListener.isPortalOrFrame(block(15, 64, 8, Material.OBSIDIAN)));
    }

    private Block block(int x, int y, int z, Material material) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> material;
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
