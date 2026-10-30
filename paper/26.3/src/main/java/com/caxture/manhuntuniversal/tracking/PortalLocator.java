package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reads one complete POI column through Paper's Mojang-mapped runtime.
 * This may read stored POI data, but never loads or generates block chunks.
 */
final class PortalLocator {
    private static final Bindings NMS = new Bindings();

    private PortalLocator() {
    }

    /** Returns only immutable coordinates; no live NMS record or stream escapes. */
    static List<PortalPoint> readChunk(World world, int chunkX, int chunkZ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Portal POI columns must be read on the server thread");
        }
        try {
            Object level = NMS.worldHandle.invoke(world);
            Object manager = NMS.poiManager.invoke(level);
            Object chunk = NMS.chunkPos.newInstance(chunkX, chunkZ);
            Predicate<Object> portal = PortalLocator::isPortal;
            try (Stream<?> records = (Stream<?>) NMS.getInChunk.invoke(
                    manager, portal, chunk, NMS.anyOccupancy)) {
                List<PortalPoint> snapshot = new ArrayList<>();
                for (Iterator<?> iterator = records.iterator(); iterator.hasNext();) {
                    Object position = NMS.recordPos.invoke(iterator.next());
                    snapshot.add(new PortalPoint((int) NMS.x.invoke(position),
                            (int) NMS.y.invoke(position), (int) NMS.z.invoke(position)));
                }
                return List.copyOf(snapshot);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read portal POIs on Paper 26.3 at "
                    + chunkX + ", " + chunkZ, exception);
        }
    }

    private static boolean isPortal(Object holder) {
        try {
            return (boolean) NMS.holderIs.invoke(holder, NMS.portalKey);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot match a portal POI on Paper 26.3", exception);
        }
    }

    // Resolve public runtime members once, using signatures verified against 26.3.
    private static final class Bindings {
        private final Method worldHandle;
        private final Method poiManager;
        private final Constructor<?> chunkPos;
        private final Method holderIs;
        private final Object portalKey;
        private final Method getInChunk;
        private final Method recordPos;
        private final Object anyOccupancy;
        private final Method x;
        private final Method y;
        private final Method z;

        private Bindings() {
            try {
                Class<?> position = Class.forName("net.minecraft.core.BlockPos");
                Class<?> chunk = Class.forName("net.minecraft.world.level.ChunkPos");
                Class<?> holder = Class.forName("net.minecraft.core.Holder");
                Class<?> key = Class.forName("net.minecraft.resources.ResourceKey");
                Class<?> manager = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager");
                Class<?> occupancy = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy");
                worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
                poiManager = Class.forName("net.minecraft.server.level.ServerLevel").getMethod("getPoiManager");
                chunkPos = chunk.getConstructor(int.class, int.class);
                holderIs = holder.getMethod("is", key);
                portalKey = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiTypes")
                        .getField("NETHER_PORTAL").get(null);
                getInChunk = manager.getMethod("getInChunk", Predicate.class, chunk, occupancy);
                recordPos = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiRecord")
                        .getMethod("getPos");
                anyOccupancy = occupancy.getField("ANY").get(null);
                x = position.getMethod("getX");
                y = position.getMethod("getY");
                z = position.getMethod("getZ");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Paper 26.3 POI runtime", exception);
            }
        }
    }
}
