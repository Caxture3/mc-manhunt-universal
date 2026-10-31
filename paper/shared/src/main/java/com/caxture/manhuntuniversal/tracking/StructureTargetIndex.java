package com.caxture.manhuntuniversal.tracking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Bounded immutable observations, always compared against the viewer's current position. */
final class StructureTargetIndex {
    private static final int MAX_PIECES = 32_768;
    private final int capacity;
    private final Map<Key, Observation> entries = new LinkedHashMap<>(16, .75f, true);
    private int pieces;

    StructureTargetIndex(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    void recordPoint(UUID world, TrackingTargetId target, PortalPoint point) {
        put(new Key(world, target, point), new Observation(point, List.of(), List.of()));
    }

    void recordRegion(UUID world, TrackingTargetId target, Bounds identity, List<Bounds> regionPieces) {
        Observation old = entries.get(new Key(world, target, identity));
        recordRegion(world, target, identity, regionPieces, old == null ? List.of() : old.spawners);
    }

    void recordRegion(UUID world, TrackingTargetId target, Bounds identity, List<Bounds> regionPieces,
                      List<PortalPoint> confirmedSpawners) {
        if (regionPieces.isEmpty()) return;
        List<PortalPoint> spawners = target == TrackingTargetId.NETHER_FORTRESS ? confirmedSpawners.stream()
                .filter(point -> regionPieces.stream().anyMatch(box -> box.contains(point.x(), point.y(), point.z())))
                .distinct().toList() : List.of();
        put(new Key(world, target, identity), new Observation(null, List.copyOf(regionPieces), spawners));
    }

    private void put(Key key, Observation value) {
        Observation old = entries.put(key, value);
        if (old != null) pieces -= old.weight();
        pieces += value.weight();
        while (entries.size() > capacity || pieces > MAX_PIECES) {
            Observation removed = entries.remove(entries.keySet().iterator().next());
            pieces -= removed.weight();
        }
    }

    Optional<TrackingPosition> nearest(TrackingTargetId target, TrackingPosition viewer,
                                        PortalPoint fallback, int radius) {
        TrackingPosition best = fallback == null ? null : at(viewer, fallback.x(), fallback.y(), fallback.z());
        double bestDistance = best == null ? Double.POSITIVE_INFINITY : squared(viewer, best);
        double maximum = (double) radius * radius;
        Key touched = null;
        for (var entry : entries.entrySet()) {
            Key key = entry.getKey();
            if (!key.world.equals(viewer.worldId()) || key.target != target) continue;
            Observation value = entry.getValue();
            if (value.point != null) {
                double dx = viewer.x() - value.point.x(), dy = viewer.y() - value.point.y(), dz = viewer.z() - value.point.z();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= maximum && distance < bestDistance) {
                    best = at(viewer, value.point.x(), value.point.y(), value.point.z());
                    bestDistance = distance;
                    touched = key;
                }
            } else for (Bounds box : value.pieces) {
                double distance = box.distanceSquared(viewer);
                if (distance <= maximum && distance < bestDistance) {
                    best = box.closest(viewer);
                    bestDistance = distance;
                    touched = key;
                }
            }
        }
        if (target == TrackingTargetId.NETHER_FORTRESS && best != null) {
            Key region = touched != null && touched.identity instanceof Bounds ? touched
                    : associatedRegion(target, viewer, best);
            if (region != null) {
                Observation selected = entries.get(region);
                PortalPoint nearestSpawner = null;
                double distance = Double.POSITIVE_INFINITY;
                for (PortalPoint point : selected.spawners) {
                    double dx = viewer.x() - point.x(), dy = viewer.y() - point.y(), dz = viewer.z() - point.z();
                    double candidate = dx * dx + dy * dy + dz * dz;
                    if (candidate <= maximum && candidate < distance) {
                        distance = candidate;
                        nearestSpawner = point;
                    }
                }
                if (nearestSpawner != null) {
                    best = at(viewer, nearestSpawner.x(), nearestSpawner.y(), nearestSpawner.z());
                    touched = region;
                }
            }
        }
        if (touched != null) entries.get(touched);
        return Optional.ofNullable(best);
    }

    private Key associatedRegion(TrackingTargetId target, TrackingPosition viewer, TrackingPosition selected) {
        Key best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var entry : entries.entrySet()) {
            Key key = entry.getKey();
            if (!key.world.equals(viewer.worldId()) || key.target != target
                    || !(key.identity instanceof Bounds box) || !box.containsXZ(selected.x(), selected.z())) continue;
            for (Bounds piece : entry.getValue().pieces) {
                double candidate = piece.distanceSquared(viewer);
                if (candidate < distance) { best = key; distance = candidate; }
            }
        }
        return best;
    }

    void pruneSpawners(BiPredicate<UUID, PortalPoint> stillValid) {
        for (var entry : entries.entrySet()) {
            Observation old = entry.getValue();
            if (old.spawners.isEmpty()) continue;
            List<PortalPoint> valid = old.spawners.stream().filter(point -> stillValid.test(entry.getKey().world, point)).toList();
            if (valid.size() != old.spawners.size()) {
                pieces -= old.spawners.size() - valid.size();
                entry.setValue(new Observation(old.point, old.pieces, valid));
            }
        }
    }

    void unload(UUID world) {
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().world.equals(world)) return false;
            pieces -= entry.getValue().weight();
            return true;
        });
    }

    void clear() { entries.clear(); pieces = 0; }
    int size() { return entries.size(); }

    private static TrackingPosition at(TrackingPosition viewer, double x, double y, double z) {
        return new TrackingPosition(viewer.worldId(), viewer.worldKind(), x, y, z);
    }
    private static double squared(TrackingPosition first, TrackingPosition second) {
        double x = first.x() - second.x(), y = first.y() - second.y(), z = first.z() - second.z();
        return x * x + y * y + z * z;
    }

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException();
        }
        TrackingPosition closest(TrackingPosition viewer) {
            return at(viewer, Math.clamp(viewer.x(), minX, maxX), Math.clamp(viewer.y(), minY, maxY),
                    Math.clamp(viewer.z(), minZ, maxZ));
        }
        double distanceSquared(TrackingPosition viewer) {
            double dx = viewer.x() - Math.clamp(viewer.x(), minX, maxX);
            double dy = viewer.y() - Math.clamp(viewer.y(), minY, maxY);
            double dz = viewer.z() - Math.clamp(viewer.z(), minZ, maxZ);
            return dx * dx + dy * dy + dz * dz;
        }
        boolean containsXZ(double x, double z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        boolean contains(double x, double y, double z) { return containsXZ(x, z) && y >= minY && y <= maxY; }
    }
    private record Key(UUID world, TrackingTargetId target, Object identity) {}
    private record Observation(PortalPoint point, List<Bounds> pieces, List<PortalPoint> spawners) {
        int weight() { return pieces.size() + spawners.size(); }
    }
}
