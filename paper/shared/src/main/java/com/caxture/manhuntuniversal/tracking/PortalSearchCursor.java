package com.caxture.manhuntuniversal.tracking;

import java.util.Objects;

/** Lazy, constant-space chunk traversal in complete square rings around the origin. */
final class PortalSearchCursor {
    record Chunk(int x, int z) {}

    private final PortalPoint origin;
    private final int centerX;
    private final int centerZ;
    private final int maximumRing;
    private final double radiusSquared;
    private int ring;
    private int position;

    PortalSearchCursor(PortalPoint origin, int radius) {
        this.origin = Objects.requireNonNull(origin, "origin");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        centerX = Math.floorDiv(origin.x(), 16);
        centerZ = Math.floorDiv(origin.z(), 16);
        maximumRing = (int) Math.max(
                Math.max(centerX - Math.floorDiv((long) origin.x() - radius, 16),
                        Math.floorDiv((long) origin.x() + radius, 16) - centerX),
                Math.max(centerZ - Math.floorDiv((long) origin.z() - radius, 16),
                        Math.floorDiv((long) origin.z() + radius, 16) - centerZ));
        radiusSquared = (double) radius * radius;
    }

    Chunk next() {
        while (ring <= maximumRing) {
            Chunk candidate;
            if (ring == 0) {
                candidate = new Chunk(centerX, centerZ);
                ring = 1;
            } else {
                int sideLength = 2 * ring;
                int side = position / sideLength;
                int offset = position % sideLength;
                candidate = switch (side) {
                    case 0 -> new Chunk(centerX - ring + offset, centerZ - ring);
                    case 1 -> new Chunk(centerX + ring, centerZ - ring + offset);
                    case 2 -> new Chunk(centerX + ring - offset, centerZ + ring);
                    default -> new Chunk(centerX - ring, centerZ + ring - offset);
                };
                if (++position == 8 * ring) {
                    ring++;
                    position = 0;
                }
            }
            if (distanceSquaredToChunk(origin, candidate) <= radiusSquared) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Every unvisited chunk lies beyond at least one side of the completed square.
     * Ignoring progress within the current ring keeps this bound conservative.
     */
    double minimumRemainingDistanceSquared() {
        if (ring > maximumRing) {
            return Double.POSITIVE_INFINITY;
        }
        if (ring == 0) {
            return 0;
        }
        long west = (long) origin.x() - (((long) centerX - ring) * 16 + 15);
        long east = ((long) centerX + ring) * 16 - origin.x();
        long north = (long) origin.z() - (((long) centerZ - ring) * 16 + 15);
        long south = ((long) centerZ + ring) * 16 - origin.z();
        long distance = Math.min(Math.min(west, east), Math.min(north, south));
        return (double) distance * distance;
    }

    /** Exact horizontal minimum for integer block positions in this chunk. */
    static double distanceSquaredToChunk(PortalPoint origin, Chunk chunk) {
        long minimumX = (long) chunk.x() * 16;
        long minimumZ = (long) chunk.z() * 16;
        long dx = Math.max(Math.max(minimumX - origin.x(), (long) origin.x() - minimumX - 15), 0);
        long dz = Math.max(Math.max(minimumZ - origin.z(), (long) origin.z() - minimumZ - 15), 0);
        return (double) dx * dx + (double) dz * dz;
    }
}
