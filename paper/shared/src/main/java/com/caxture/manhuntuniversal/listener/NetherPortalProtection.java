package com.caxture.manhuntuniversal.listener;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;

/** Local-only checks; never loads a neighbouring chunk or enumerates a portal. */
final class NetherPortalProtection {
    private static final BlockFace[] NEIGHBOURS = {BlockFace.UP, BlockFace.DOWN,
            BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};

    static boolean isProtected(Block block, Material original, boolean active) {
        if (!active || original != Material.NETHER_PORTAL && original != Material.OBSIDIAN
                || block.getWorld().getEnvironment() != World.Environment.NETHER) return false;
        if (original == Material.NETHER_PORTAL) return true;
        World world = block.getWorld();
        for (BlockFace face : NEIGHBOURS) {
            int x = block.getX() + face.getModX();
            int y = block.getY() + face.getModY();
            int z = block.getZ() + face.getModZ();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
            Block neighbour = world.getBlockAt(x, y, z);
            if (neighbour.getType() != Material.NETHER_PORTAL) continue;
            if (face.getModY() != 0) return true;
            if (!(neighbour.getBlockData() instanceof Orientable portal)) return true;
            if (portal.getAxis() == Axis.X && face.getModX() != 0
                    || portal.getAxis() == Axis.Z && face.getModZ() != 0) return true;
        }
        return false;
    }
}
