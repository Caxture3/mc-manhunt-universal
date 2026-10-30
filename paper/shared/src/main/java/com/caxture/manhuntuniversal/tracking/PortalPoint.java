package com.caxture.manhuntuniversal.tracking;

/** Immutable block coordinates; safe to pass to background calculations. */
record PortalPoint(int x, int y, int z) {
    double distanceSquared(PortalPoint other) {
        double dx = (double) x - other.x;
        double dy = (double) y - other.y;
        double dz = (double) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
