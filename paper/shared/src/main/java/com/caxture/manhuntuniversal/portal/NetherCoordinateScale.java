package com.caxture.manhuntuniversal.portal;

import org.bukkit.Location;
import org.bukkit.World;

/** Shared horizontal projection for native portal search and compass search inputs. */
public final class NetherCoordinateScale {
    private static final double BORDER_EPSILON = 1.0E-5;
    private NetherCoordinateScale() { }

    public static boolean applies(World from, World to) {
        if (from == null || to == null) return false;
        return from.getEnvironment() == World.Environment.NORMAL && to.getEnvironment() == World.Environment.NETHER
                || from.getEnvironment() == World.Environment.NETHER && to.getEnvironment() == World.Environment.NORMAL;
    }

    /** Keeps target world/Y/rotation. A new location is returned; neither input is modified. */
    public static Location project(Location from, Location target, int scale) {
        return project(from, target, scale, Integer.MAX_VALUE);
    }

    /** Production callers supply the server's max-world-size, which can be smaller than the default border. */
    public static Location project(Location from, Location target, int scale, int maximumWorldSize) {
        if (scale < 1) throw new IllegalArgumentException("Nether coordinate scale must be positive");
        if (maximumWorldSize < 1) throw new IllegalArgumentException("Maximum world size must be positive");
        Location result = target.clone();
        if (!applies(from.getWorld(), target.getWorld())) return result;
        boolean enteringNether = target.getWorld().getEnvironment() == World.Environment.NETHER;
        double x = enteringNether ? from.getX() / scale : from.getX() * scale;
        double z = enteringNether ? from.getZ() / scale : from.getZ() * scale;
        var border = target.getWorld().getWorldBorder();
        Location center = border.getCenter();
        double half = border.getSize() / 2.0;
        double limit = Math.min(border.getMaxCenterCoordinate(), maximumWorldSize);
        result.setX(clamp(x, bound(center.getX() - half, limit), bound(center.getX() + half, limit)));
        result.setZ(clamp(z, bound(center.getZ() - half, limit), bound(center.getZ() + half, limit)));
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return value < minimum ? minimum : Math.min(maximum - BORDER_EPSILON, value);
    }
    private static double bound(double value, double limit) { return Math.max(-limit, Math.min(limit, value)); }
}
