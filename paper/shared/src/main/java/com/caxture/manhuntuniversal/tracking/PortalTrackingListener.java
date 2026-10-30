package com.caxture.manhuntuniversal.tracking;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Forwards synchronous portal changes; the service coalesces delayed invalidation. */
public final class PortalTrackingListener implements Listener {
    private final CompassTrackingService tracking;

    public PortalTrackingListener(CompassTrackingService tracking) {
        this.tracking = tracking;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        for (BlockState block : event.getBlocks()) {
            tracking.portalBlocksChanged(block.getWorld(), block.getX(), block.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.NETHER_PORTAL) {
            changed(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isPortalOrFrame(event.getBlock())) {
            changed(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (isPortalOrFrame(block)) {
                changed(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (isPortalOrFrame(block)) {
                changed(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.NETHER_PORTAL) {
            changed(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        tracking.worldUnloaded(event.getWorld().getUID());
    }

    static boolean isPortalOrFrame(Block block) {
        Material material = block.getType();
        if (material == Material.NETHER_PORTAL) return true;
        if (material != Material.OBSIDIAN) return false;
        var world = block.getWorld();
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            int x = block.getX() + face.getModX();
            int y = block.getY() + face.getModY();
            int z = block.getZ() + face.getModZ();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            // Unknown border may contain a portal: invalidate conservatively without loading it.
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
            if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) return true;
        }
        return false;
    }

    private void changed(Block block) {
        tracking.portalBlocksChanged(block.getWorld(), block.getX(), block.getZ());
    }
}
