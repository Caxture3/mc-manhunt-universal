package com.caxture.manhuntuniversal.tracking;

import io.papermc.paper.entity.poi.PoiType;
import io.papermc.paper.registry.keys.PoiTypeKeys;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;

/** Original Paper POI lookup, isolated from versions without the public POI API. */
final class PortalLocator {
    private PortalLocator() {
    }

    static Location findNearest(World world, Location origin, int radius) {
        PoiType portalType = Registry.POINT_OF_INTEREST_TYPE.get(PoiTypeKeys.NETHER_PORTAL);
        return portalType == null ? null : world.locateNearestPoi(origin, portalType, radius);
    }
}
