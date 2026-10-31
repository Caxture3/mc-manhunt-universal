package com.caxture.manhuntuniversal.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NetherPortalProtectionListenerTest {
    private final PortalProtectionFixture scene = new PortalProtectionFixture();
    private boolean running = true;
    private boolean enabled = true;
    private int denied;
    private final NetherPortalProtectionListener listener = new NetherPortalProtectionListener(
            () -> running, () -> enabled, player -> denied++);

    @Test void protectsAllPlayersWithoutPrivilegeBypassAndHonoursLiveRunningConfig() {
        var event = new BlockBreakEvent(scene.block(7, 64, 8), scene.player);
        listener.onBreak(event);
        assertTrue(event.isCancelled());
        assertEquals(1, denied);
        for (int mode = 0; mode < 3; mode++) {
            running = mode != 0;
            enabled = mode != 1;
            scene.environment = mode == 2 ? World.Environment.NORMAL : World.Environment.NETHER;
            var allowed = new BlockBreakEvent(scene.block(8, 64, 8), scene.player);
            listener.onBreak(allowed);
            assertFalse(allowed.isCancelled());
        }
    }

    @Test void placingUsesOriginalStateAndChecksAllMultiPlaceBlocks() {
        var old = scene.state(8, 64, 8, Material.NETHER_PORTAL);
        scene.set(8, 64, 8, Material.STONE);
        var placed = new BlockPlaceEvent(scene.block(8, 64, 8), old, scene.block(8, 63, 8),
                scene.stack(Material.STONE), scene.player, true, EquipmentSlot.HAND);
        listener.onPlace(placed);
        assertTrue(placed.isCancelled());
        var multi = new BlockMultiPlaceEvent(List.of(scene.state(1, 64, 1, Material.AIR), old),
                scene.block(1, 63, 1), scene.stack(Material.RED_BED), scene.player, true, EquipmentSlot.HAND);
        listener.onPlace(multi);
        assertTrue(multi.isCancelled());
    }

    @Test void explosionsKeepUnrelatedDamageWhileProtectingBodyAndFrame() {
        scene.set(4, 64, 4, Material.NETHERRACK);
        scene.set(3, 64, 3, Material.OBSIDIAN);
        List<Block> blocks = new ArrayList<>(List.of(scene.block(8, 64, 8), scene.block(7, 64, 8),
                scene.block(4, 64, 4), scene.block(3, 64, 3)));
        var event = new BlockExplodeEvent(scene.block(2, 64, 2), scene.state(2, 64, 2, Material.TNT),
                blocks, 1, ExplosionResult.DESTROY);
        listener.onBlockExplode(event);
        assertFalse(event.isCancelled());
        assertEquals(2, event.blockList().size());
        var entity = new EntityExplodeEvent(scene.player, scene.block(2, 64, 2).getLocation(),
                new ArrayList<>(List.of(scene.block(8, 64, 8))), 1, ExplosionResult.DESTROY);
        listener.onEntityExplode(entity);
        assertTrue(entity.blockList().isEmpty());
    }

    @Test void directPhysicsDestructionAndEntityReplacementAreBlocked() {
        var portal = scene.block(8, 64, 8);
        var destroy = new BlockDestroyEvent(portal, scene.data(Material.AIR), scene.data(Material.NETHER_PORTAL), 0, false);
        listener.onDestroy(destroy);
        assertTrue(destroy.isCancelled());
        var physics = new BlockPhysicsEvent(portal, scene.data(Material.AIR));
        listener.onPhysics(physics);
        assertTrue(physics.isCancelled());
        var entity = new EntityChangeBlockEvent(scene.player, portal, scene.data(Material.SAND));
        listener.onEntityChange(entity);
        assertTrue(entity.isCancelled());
    }

    @Test void fluidFlowBucketsAndDispenserCannotReplacePortalButArrowsAreAllowed() {
        var portal = scene.block(8, 64, 8);
        var flow = new BlockFromToEvent(scene.block(8, 64, 7), portal);
        listener.onFromTo(flow);
        assertTrue(flow.isCancelled());
        var bucket = new PlayerBucketEmptyEvent(scene.player, portal, scene.block(1, 64, 1),
                BlockFace.EAST, Material.LAVA_BUCKET, scene.stack(Material.LAVA_BUCKET), EquipmentSlot.HAND);
        listener.onBucket(bucket);
        assertTrue(bucket.isCancelled()); // actual destination, not clicked block
        scene.facing = BlockFace.SOUTH;
        scene.set(8, 64, 7, Material.DISPENSER);
        var dispense = new BlockDispenseEvent(scene.block(8, 64, 7), scene.stack(Material.LAVA_BUCKET), new Vector(8, 64, 8));
        listener.onDispense(dispense);
        assertTrue(dispense.isCancelled());
        var arrow = new BlockDispenseEvent(scene.block(8, 64, 7), scene.stack(Material.ARROW), new Vector(8, 64, 8));
        listener.onDispense(arrow);
        assertFalse(arrow.isCancelled());
    }

    @Test void droppersAndDispenserBucketFallbackDoNotTreatItemVelocityAsWorldCoordinates() {
        scene.neighboursLoaded = false;
        scene.set(2, 64, 2, Material.DROPPER);
        var dropped = new BlockDispenseEvent(scene.block(2, 64, 2), scene.stack(Material.LAVA_BUCKET),
                new Vector(-.1, .2, -.1));
        listener.onDispense(dropped);
        assertFalse(dropped.isCancelled());
        scene.set(2, 64, 2, Material.DISPENSER);
        scene.set(3, 64, 2, Material.STONE);
        var fallback = new BlockDispenseEvent(scene.block(2, 64, 2), scene.stack(Material.LAVA_BUCKET),
                new Vector(-.1, .2, -.1));
        listener.onDispense(fallback);
        assertFalse(fallback.isCancelled());
    }

    @Test void pistonChecksHeadAndActualMovedBlockDirectionIncludingRetraction() {
        scene.set(6, 64, 8, Material.PISTON);
        var extend = new BlockPistonExtendEvent(scene.block(6, 64, 8), List.of(), BlockFace.EAST);
        listener.onPistonExtend(extend); // piston head would replace required frame at x=7
        assertTrue(extend.isCancelled());
        scene.set(11, 64, 8, Material.STONE);
        var retract = new BlockPistonRetractEvent(scene.block(13, 64, 8),
                List.of(scene.block(11, 64, 8)), BlockFace.WEST);
        listener.onPistonRetract(retract); // actual destination is frame at x=10
        assertTrue(retract.isCancelled());
    }

    @Test void creationCannotOverwriteExistingPortalButNewPortalOnEmptySpaceIsAllowed() {
        var overwritten = new PortalCreateEvent(List.of(scene.state(8, 64, 8, Material.AIR)), scene.world,
                scene.player, PortalCreateEvent.CreateReason.NETHER_PAIR);
        listener.onPortalCreate(overwritten);
        assertTrue(overwritten.isCancelled());
        var create = new PortalCreateEvent(List.of(scene.state(2, 64, 2, Material.NETHER_PORTAL)), scene.world,
                scene.player, PortalCreateEvent.CreateReason.FIRE);
        listener.onPortalCreate(create);
        assertFalse(create.isCancelled());
    }

    @Test void environmentalFadingBurningAndFormingDoNotExtinguishThePortal() {
        var portal = scene.block(8, 64, 8);
        var fade = new BlockFadeEvent(portal, scene.state(8, 64, 8, Material.AIR));
        listener.onFade(fade);
        assertTrue(fade.isCancelled());
        var burn = new BlockBurnEvent(portal, scene.block(8, 64, 7));
        listener.onBurn(burn);
        assertTrue(burn.isCancelled());
        var form = new BlockFormEvent(portal, scene.state(8, 64, 8, Material.STONE));
        listener.onForm(form);
        assertTrue(form.isCancelled());
    }
}
