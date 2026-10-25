package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Paper 1.21.11 has no public POI lookup API. Invoke the same vanilla nearest-POI
 * query through its Mojang-mapped runtime; never create portals or scan blocks.
 * Called by the existing main-thread tracking search queue.
 */
final class PortalLocator {
    private static final Bindings NMS = new Bindings();

    private PortalLocator() {
    }

    static Location findNearest(World world, Location origin, int radius) {
        try {
            Object level = NMS.worldHandle.invoke(world);
            Object manager = NMS.poiManager.invoke(level);
            Object source = NMS.blockPos.newInstance(
                    origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
            Predicate<Object> portal = holder -> {
                try {
                    return (boolean) NMS.holderIs.invoke(holder, NMS.portalKey);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot match a portal POI on Paper 1.21.11", exception);
                }
            };
            Optional<?> result = (Optional<?>) NMS.findClosest.invoke(
                    manager, portal, source, radius, NMS.anyOccupancy);
            Object position = result.orElse(null);
            return position == null ? null : new Location(world,
                    (int) NMS.x.invoke(position), (int) NMS.y.invoke(position), (int) NMS.z.invoke(position));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot locate portal POIs on Paper 1.21.11", exception);
        }
    }

    // Resolve public runtime members once, using signatures verified against 1.21.11.
    private static final class Bindings {
        private final Method worldHandle;
        private final Method poiManager;
        private final Constructor<?> blockPos;
        private final Method holderIs;
        private final Object portalKey;
        private final Method findClosest;
        private final Object anyOccupancy;
        private final Method x;
        private final Method y;
        private final Method z;

        private Bindings() {
            try {
                Class<?> position = Class.forName("net.minecraft.core.BlockPos");
                Class<?> holder = Class.forName("net.minecraft.core.Holder");
                Class<?> key = Class.forName("net.minecraft.resources.ResourceKey");
                Class<?> manager = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager");
                Class<?> occupancy = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy");
                worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
                poiManager = Class.forName("net.minecraft.server.level.ServerLevel").getMethod("getPoiManager");
                blockPos = position.getConstructor(int.class, int.class, int.class);
                holderIs = holder.getMethod("is", key);
                portalKey = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiTypes")
                        .getField("NETHER_PORTAL").get(null);
                findClosest = manager.getMethod("findClosest", Predicate.class, position, int.class, occupancy);
                anyOccupancy = occupancy.getField("ANY").get(null);
                x = position.getMethod("getX");
                y = position.getMethod("getY");
                z = position.getMethod("getZ");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Paper 1.21.11 POI runtime", exception);
            }
        }
    }
}
