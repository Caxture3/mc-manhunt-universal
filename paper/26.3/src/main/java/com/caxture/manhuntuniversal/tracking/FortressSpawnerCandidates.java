package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Bukkit;
import org.bukkit.generator.structure.GeneratedStructure;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads the generated fortress pieces without loading or inspecting world blocks. */
final class FortressSpawnerCandidates {
    private FortressSpawnerCandidates() {
    }

    /** Candidates still require a live blaze-spawner check, including after a spawner is broken. */
    static List<PortalPoint> positions(GeneratedStructure generated) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Fortress spawner candidates must be read on the server thread");
        }
        Objects.requireNonNull(generated, "generated");
        try {
            Bindings nms = RuntimeBindings.INSTANCE;
            Object start = nms.structureHandle.get(generated);
            List<PortalPoint> positions = new ArrayList<>();
            for (Object piece : (List<?>) nms.pieces.invoke(start)) {
                if (nms.monsterThrone.isInstance(piece)) {
                    // MonsterThrone.postProcess uses this local position in Minecraft 26.3.
                    Object position = nms.worldPosition.invoke(piece, 3, 5, 5);
                    positions.add(new PortalPoint((int) nms.x.invoke(position),
                            (int) nms.y.invoke(position), (int) nms.z.invoke(position)));
                }
            }
            return List.copyOf(positions);
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read fortress spawner candidates on Paper 26.3", exception);
        }
    }

    private static final class RuntimeBindings {
        private static final Bindings INSTANCE = new Bindings();
    }

    // Private/protected Mojang-mapped members verified against Paper and Minecraft 26.3.
    private static final class Bindings {
        private final Field structureHandle;
        private final Method pieces;
        private final Class<?> monsterThrone;
        private final Method worldPosition;
        private final Method x;
        private final Method y;
        private final Method z;

        private Bindings() {
            try {
                structureHandle = Class.forName("org.bukkit.craftbukkit.generator.structure.CraftGeneratedStructure")
                        .getDeclaredField("handle");
                if (!structureHandle.trySetAccessible()) {
                    throw new IllegalStateException("Cannot access CraftGeneratedStructure.handle");
                }
                pieces = Class.forName("net.minecraft.world.level.levelgen.structure.StructureStart")
                        .getMethod("getPieces");
                monsterThrone = Class.forName("net.minecraft.world.level.levelgen.structure.structures."
                        + "NetherFortressPieces$MonsterThrone");
                worldPosition = Class.forName("net.minecraft.world.level.levelgen.structure.StructurePiece")
                        .getDeclaredMethod("getWorldPos", int.class, int.class, int.class);
                if (!worldPosition.trySetAccessible()) {
                    throw new IllegalStateException("Cannot access StructurePiece.getWorldPos");
                }
                Class<?> position = Class.forName("net.minecraft.core.Vec3i");
                x = position.getMethod("getX");
                y = position.getMethod("getY");
                z = position.getMethod("getZ");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                throw new IllegalStateException("Unsupported Paper 26.3 fortress piece runtime", exception);
            }
        }
    }
}
