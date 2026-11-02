package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.portal.NetherCoordinateScale;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Optional;

/** Keeps genuine world history distinct from a projected search/movement reference. */
final class TrackingCoordinateProjection {
    private TrackingCoordinateProjection() { }
    static Location origin(Location from, World target, Optional<TrackingPosition> visited, int scale, int maximumWorldSize) {
        if (from.getWorld().equals(target)) return from.clone();
        if (visited.isPresent()) return location(target, visited.get());
        return projectedOrSpawn(from, target, scale, maximumWorldSize);
    }
    static Location reference(Location from, World target, Optional<TrackingPosition> visited, int scale, int maximumWorldSize) {
        if (from.getWorld().equals(target)) return from.clone();
        if (NetherCoordinateScale.applies(from.getWorld(), target)) return projectedOrSpawn(from, target, scale, maximumWorldSize);
        return visited.map(value -> location(target, value)).orElseGet(target::getSpawnLocation);
    }
    private static Location projectedOrSpawn(Location from, World target, int scale, int maximumWorldSize) {
        Location spawn = target.getSpawnLocation();
        return NetherCoordinateScale.applies(from.getWorld(), target)
                ? NetherCoordinateScale.project(from, new Location(target, 0, spawn.getY(), 0), scale, maximumWorldSize) : spawn;
    }
    private static Location location(World world, TrackingPosition point) {
        return new Location(world, point.x(), point.y(), point.z());
    }
}
