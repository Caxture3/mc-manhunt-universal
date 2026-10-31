package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reads structure references from an already loaded chunk without loading its starts. */
final class StructureChunkReferences {
    private StructureChunkReferences() {
    }

    static Set<PortalSearchCursor.Chunk> starts(
            World world, int chunkX, int chunkZ, TrackingTargetId target) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Structure references must be read on the server thread");
        }
        if (target == TrackingTargetId.NONE) {
            return Set.of();
        }
        try {
            Bindings nms = RuntimeBindings.INSTANCE;
            Object level = nms.worldHandle.invoke(world);
            Object chunk = nms.chunkIfLoaded.invoke(level, chunkX, chunkZ);
            if (chunk == null) {
                return Set.of();
            }
            Map<?, ?> references = (Map<?, ?>) nms.allReferences.invoke(chunk);
            Set<PortalSearchCursor.Chunk> starts = new HashSet<>();
            for (Map.Entry<?, ?> entry : references.entrySet()) {
                Structure structure = (Structure) nms.structureToBukkit.invoke(null, entry.getKey());
                if (!matches(structure.getKey(), target)) {
                    continue;
                }
                // Fastutil LongSet implements Iterable<Long>; only coordinates escape this call.
                for (Object reference : (Iterable<?>) entry.getValue()) {
                    long packed = (Long) reference;
                    starts.add(new PortalSearchCursor.Chunk((int) packed, (int) (packed >> 32)));
                }
            }
            return Set.copyOf(starts);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read structure references on Paper 1.21.11 at "
                    + chunkX + ", " + chunkZ, exception);
        }
    }

    private static boolean matches(NamespacedKey key, TrackingTargetId target) {
        if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
            return false;
        }
        return switch (target) {
            case VILLAGE -> switch (key.getKey()) {
                case "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga" -> true;
                default -> false;
            };
            case NETHER_FORTRESS -> key.getKey().equals("fortress");
            case BASTION_REMNANT -> key.getKey().equals("bastion_remnant");
            case NONE -> false;
        };
    }

    private static final class RuntimeBindings {
        private static final Bindings INSTANCE = new Bindings();
    }

    // Public Mojang-mapped signatures verified against Paper 1.21.11 and its MC jar.
    private static final class Bindings {
        private final Method worldHandle;
        private final Method chunkIfLoaded;
        private final Method allReferences;
        private final Method structureToBukkit;

        private Bindings() {
            try {
                worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
                chunkIfLoaded = Class.forName("net.minecraft.server.level.ServerLevel")
                        .getMethod("getChunkIfLoaded", int.class, int.class);
                allReferences = Class.forName("net.minecraft.world.level.chunk.ChunkAccess")
                        .getMethod("getAllReferences");
                structureToBukkit = Class.forName("org.bukkit.craftbukkit.generator.structure.CraftStructure")
                        .getMethod("minecraftToBukkit",
                                Class.forName("net.minecraft.world.level.levelgen.structure.Structure"));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Paper 1.21.11 structure reference runtime", exception);
            }
        }
    }
}
