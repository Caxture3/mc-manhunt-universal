package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BoundingBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Reads Bukkit structure geometry only after every referenced start is already resident. */
final class PaperStructureProbeSource implements NearbyStructureProbe.Source {
    private final Server server;
    PaperStructureProbeSource(Server server) { this.server = server; }

    @Override public boolean loaded(UUID id, PortalSearchCursor.Chunk chunk) {
        World world = server.getWorld(id);
        return world != null && world.isChunkLoaded(chunk.x(), chunk.z());
    }
    @Override public Set<PortalSearchCursor.Chunk> starts(UUID id, PortalSearchCursor.Chunk chunk, TrackingTargetId target) {
        World world = server.getWorld(id);
        return world == null ? Set.of() : StructureChunkReferences.starts(world, chunk.x(), chunk.z(), target);
    }
    @Override public CompletableFuture<Boolean> load(UUID id, PortalSearchCursor.Chunk chunk) {
        World world = server.getWorld(id);
        if (world == null) return CompletableFuture.completedFuture(false);
        // Shared by located targets, referenced starts and template spawner candidates; never generate terrain.
        return world.getChunkAtAsync(chunk.x(), chunk.z(), false, false).thenApply(value -> value != null);
    }
    @Override public List<NearbyStructureProbe.Region> regions(UUID id, PortalSearchCursor.Chunk chunk, TrackingTargetId target) {
        World world = server.getWorld(id);
        if (world == null || !loaded(id, chunk)) return List.of();
        Set<PortalSearchCursor.Chunk> refs = starts(id, chunk, target);
        if (refs.stream().anyMatch(start -> !loaded(id, start))) return List.of();
        List<NearbyStructureProbe.Region> result = new ArrayList<>();
        List<PortalPoint> observedSpawners = new ArrayList<>();
        if (target == TrackingTargetId.NETHER_FORTRESS) {
            for (var state : world.getChunkAt(chunk.x(), chunk.z()).getTileEntities(false)) {
                if (state instanceof CreatureSpawner spawner && spawner.getSpawnedType() == EntityType.BLAZE) {
                    observedSpawners.add(new PortalPoint(state.getX(), state.getY(), state.getZ()));
                }
            }
        }
        for (Structure type : types(target)) for (var generated : world.getStructures(chunk.x(), chunk.z(), type)) {
            var pieces = generated.getPieces().stream().map(piece -> bounds(piece.getBoundingBox())).toList();
            Set<PortalPoint> candidates = new LinkedHashSet<>();
            if (target == TrackingTargetId.NETHER_FORTRESS) {
                candidates.addAll(FortressSpawnerCandidates.positions(generated));
                observedSpawners.stream().filter(point -> pieces.stream()
                        .anyMatch(box -> box.contains(point.x(), point.y(), point.z()))).forEach(candidates::add);
            }
            result.add(new NearbyStructureProbe.Region(bounds(generated.getBoundingBox()), pieces, List.copyOf(candidates)));
        }
        return List.copyOf(result);
    }

    @Override public List<PortalPoint> spawners(UUID id, List<PortalPoint> candidates) {
        World world = server.getWorld(id);
        if (world == null || candidates.isEmpty()) return List.of();
        List<PortalPoint> found = new ArrayList<>();
        for (PortalPoint point : candidates) {
            if (point.y() < world.getMinHeight() || point.y() >= world.getMaxHeight()
                    || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) continue;
            var block = world.getBlockAt(point.x(), point.y(), point.z());
            if (block.getType() == Material.SPAWNER && block.getState(false) instanceof CreatureSpawner spawner
                    && spawner.getSpawnedType() == EntityType.BLAZE) found.add(point);
        }
        return List.copyOf(found);
    }
    private static List<Structure> types(TrackingTargetId target) {
        return switch (target) {
            case VILLAGE -> List.of(Structure.VILLAGE_PLAINS, Structure.VILLAGE_DESERT, Structure.VILLAGE_SAVANNA,
                    Structure.VILLAGE_SNOWY, Structure.VILLAGE_TAIGA);
            case NETHER_FORTRESS -> List.of(Structure.FORTRESS);
            case BASTION_REMNANT -> List.of(Structure.BASTION_REMNANT);
            case NONE -> List.of();
        };
    }
    private static StructureTargetIndex.Bounds bounds(BoundingBox box) {
        return new StructureTargetIndex.Bounds(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }
}
