package com.caxture.manhuntuniversal.listener;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetherPortalProtectionTest {
    private final PortalProtectionFixture scene = new PortalProtectionFixture();

    @Test void protectsPortalAndRequiredFrameButNotOptionalCornersOrDecorativeObsidian() {
        assertTrue(protectedAt(8, 64, 8));
        assertTrue(protectedAt(7, 64, 8));
        assertTrue(protectedAt(8, 63, 8));
        assertTrue(protectedAt(8, 67, 8));
        assertFalse(protectedAt(7, 63, 8));
        scene.set(8, 64, 7, Material.OBSIDIAN);
        assertFalse(protectedAt(8, 64, 7));
        scene.set(3, 64, 3, Material.OBSIDIAN);
        assertFalse(protectedAt(3, 64, 3));
        assertFalse(protectedAt(2, 64, 2));
    }

    @Test void disabledAndOtherDimensionsRemainUnrestricted() {
        assertFalse(NetherPortalProtection.isProtected(scene.block(8, 64, 8), Material.NETHER_PORTAL, false));
        scene.environment = World.Environment.NORMAL;
        assertFalse(protectedAt(8, 64, 8));
        scene.environment = World.Environment.THE_END;
        assertFalse(protectedAt(7, 64, 8));
    }

    @Test void rotatedPortalUsesItsOwnPlaneAndOriginalTypeIsUsedAfterReplacement() {
        scene.axis = Axis.Z;
        scene.set(8, 64, 7, Material.OBSIDIAN);
        assertTrue(protectedAt(8, 64, 7));
        assertFalse(protectedAt(7, 64, 8));
        scene.set(8, 64, 8, Material.STONE);
        assertTrue(NetherPortalProtection.isProtected(scene.block(8, 64, 8), Material.NETHER_PORTAL, true));
    }

    @Test void unloadedBoundaryIsConservativeWithoutReadingOrLoadingThatNeighbour() {
        scene.neighboursLoaded = false;
        scene.set(15, 64, 8, Material.OBSIDIAN);
        assertTrue(protectedAt(15, 64, 8));
    }

    private boolean protectedAt(int x, int y, int z) {
        var block = scene.block(x, y, z);
        return NetherPortalProtection.isProtected(block, block.getType(), true);
    }
}
