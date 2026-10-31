package com.caxture.manhuntuniversal.listener;

import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.SessionState;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Prevents vanilla changes before commit; no world scans, rollback loop or privilege bypass. */
public final class NetherPortalProtectionListener implements Listener {
    private final BooleanSupplier running;
    private final BooleanSupplier enabled;
    private final Consumer<Player> denied;

    public NetherPortalProtectionListener(GameManager games, ConfigurationService configuration, LanguageService language) {
        this(() -> games.activeSession().filter(session -> session.state() == SessionState.RUNNING).isPresent(),
                () -> configuration.get(BooleanConfigKey.PREVENT_NETHER_PORTAL_DESTRUCTION),
                player -> language.send(player, "restriction.nether-portal-destruction-denied"));
    }

    NetherPortalProtectionListener(BooleanSupplier running, BooleanSupplier enabled, Consumer<Player> denied) {
        this.running = running;
        this.enabled = enabled;
        this.denied = denied;
    }

    private boolean active() { return running.getAsBoolean() && enabled.getAsBoolean(); }
    private boolean protectedBlock(Block block) { return protectedBlock(block, block.getType()); }
    private boolean protectedBlock(Block block, Material original) {
        if (original != Material.NETHER_PORTAL && original != Material.OBSIDIAN) return false;
        return NetherPortalProtection.isProtected(block, original, active());
    }
    private void cancel(Block block, Cancellable event) {
        if (protectedBlock(block)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
            denied.accept(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // Multi-place inherits the BlockPlaceEvent handler list; inspect every original state.
        List<BlockState> originals = event instanceof BlockMultiPlaceEvent multi
                ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
        if (originals.stream().anyMatch(old -> protectedBlock(old.getBlock(), old.getType()))) {
            event.setCancelled(true);
            event.setBuild(false);
            denied.accept(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NETHER_PORTAL) cancel(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) { cancel(event.getToBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
            denied.accept(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        String item = event.getItem().getType().name();
        if (!item.endsWith("_BUCKET") || item.equals("MILK_BUCKET")
                || event.getBlock().getType() != Material.DISPENSER || !active()
                || event.getBlock().getWorld().getEnvironment() != World.Environment.NETHER) return;
        if (!(event.getBlock().getBlockData() instanceof Directional dispenser)) return;
        Block source = event.getBlock();
        BlockFace face = dispenser.getFacing();
        // Native bucket placement uses the facing block. A fallback item toss has a velocity,
        // not target coordinates; it cannot replace the solid frame in front of the dispenser.
        Block destination = loadedBlock(source.getWorld(), source.getX() + face.getModX(),
                source.getY() + face.getModY(), source.getZ() + face.getModZ());
        if (destination == null || destination.getType() == Material.NETHER_PORTAL && protectedBlock(destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!active() || event.getBlock().getWorld().getEnvironment() != World.Environment.NETHER) return;
        BlockFace facing = event.getBlock().getBlockData() instanceof Directional piston
                ? piston.getFacing() : event.getDirection();
        if (protectedDestination(event.getBlock(), facing) || movesProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (active() && event.getBlock().getWorld().getEnvironment() == World.Environment.NETHER
                && movesProtected(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    private boolean movesProtected(List<Block> moved, BlockFace direction) {
        return moved.stream().anyMatch(block -> protectedBlock(block) || protectedDestination(block, direction));
    }

    private boolean protectedDestination(Block source, BlockFace direction) {
        Block destination = loadedBlock(source.getWorld(), source.getX() + direction.getModX(),
                source.getY() + direction.getModY(), source.getZ() + direction.getModZ());
        return destination == null || protectedBlock(destination);
    }

    private static Block loadedBlock(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return null;
        return world.getBlockAt(x, y, z);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) { cancel(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (event.getBlocks().stream().anyMatch(this::changesProtected)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getBlocks().stream().anyMatch(this::changesProtected)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        // Native cross-dimension portal creation can directly overwrite existing blocks.
        if (event.getBlocks().stream().anyMatch(this::changesProtected)) event.setCancelled(true);
    }

    private boolean changesProtected(BlockState update) {
        Block current = update.getBlock();
        return protectedBlock(current) && !update.getBlockData().equals(current.getBlockData());
    }
}
