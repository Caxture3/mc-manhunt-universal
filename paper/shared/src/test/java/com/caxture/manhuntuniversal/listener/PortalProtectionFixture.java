package com.caxture.manhuntuniversal.listener;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalProtectionFixture {
    record Point(int x, int y, int z) {}
    final Map<Point, Material> blocks = new HashMap<>();
    World.Environment environment = World.Environment.NETHER;
    Axis axis = Axis.X;
    BlockFace facing = BlockFace.EAST;
    boolean neighboursLoaded = true;
    final World world = proxy(World.class, (method, args) -> switch (method) {
        case "getEnvironment" -> environment;
        case "getMinHeight" -> 0;
        case "getMaxHeight" -> 256;
        case "isChunkLoaded" -> neighboursLoaded || (int) args[0] == 0 && (int) args[1] == 0;
        case "getBlockAt" -> {
            int x = (int) args[0], z = (int) args[2];
            assertTrue(neighboursLoaded || x >> 4 == 0 && z >> 4 == 0, "must not load neighbour chunks");
            yield block(x, (int) args[1], z);
        }
        case "getUID" -> new UUID(0, 9);
        case "getName" -> "test-nether";
        default -> throw new UnsupportedOperationException(method);
    });
    final Player player = proxy(Player.class, (method, args) -> switch (method) {
        case "getUniqueId" -> new UUID(0, 1);
        case "getName" -> "CreativeOp";
        default -> throw new UnsupportedOperationException("must not check player privilege: " + method);
    });

    PortalProtectionFixture() {
        for (int x = 7; x <= 10; x++) for (int y = 63; y <= 67; y++) {
            set(x, y, 8, x == 7 || x == 10 || y == 63 || y == 67 ? Material.OBSIDIAN : Material.NETHER_PORTAL);
        }
    }

    void set(int x, int y, int z, Material material) { blocks.put(new Point(x, y, z), material); }
    Block block(int x, int y, int z) {
        return proxy(Block.class, (method, args) -> switch (method) {
            case "getType" -> blocks.getOrDefault(new Point(x, y, z), Material.AIR);
            case "getWorld" -> world;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            case "getBlockData" -> data(blocks.getOrDefault(new Point(x, y, z), Material.AIR));
            case "getLocation" -> new Location(world, x, y, z);
            case "getRelative" -> args[0] instanceof BlockFace face
                    ? block(x + face.getModX(), y + face.getModY(), z + face.getModZ())
                    : block(x + (int) args[0], y + (int) args[1], z + (int) args[2]);
            case "getState" -> state(x, y, z, blocks.getOrDefault(new Point(x, y, z), Material.AIR));
            default -> throw new UnsupportedOperationException(method);
        });
    }

    BlockState state(int x, int y, int z, Material original) {
        return proxy(BlockState.class, (method, args) -> switch (method) {
            case "getType" -> original;
            case "getBlock" -> block(x, y, z);
            case "getWorld" -> world;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            case "getBlockData" -> data(original);
            default -> throw new UnsupportedOperationException(method);
        });
    }

    BlockData data(Material material) {
        Class<? extends BlockData> type = material == Material.NETHER_PORTAL ? Orientable.class
                : material == Material.DISPENSER || material == Material.PISTON ? Directional.class : BlockData.class;
        return proxy(type, (method, args) -> switch (method) {
            case "getMaterial" -> material;
            case "getAxis" -> axis;
            case "getFacing" -> facing;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    ItemStack stack(Material material) { return new Stack(material); }
    private static final class Stack extends ItemStack {
        private final Material type;
        Stack(Material type) { this.type = type; }
        @Override public Material getType() { return type; }
        @Override public int getAmount() { return 1; }
        @Override public Stack clone() { return new Stack(type); }
    }

    @FunctionalInterface interface Invoke { Object call(String method, Object[] args); }
    static <T> T proxy(Class<T> type, Invoke invoke) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (value, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(value);
                    case "equals" -> value == args[0];
                    case "toString" -> "Test " + type.getSimpleName();
                    default -> invoke.call(method.getName(), args);
                }));
    }
}
