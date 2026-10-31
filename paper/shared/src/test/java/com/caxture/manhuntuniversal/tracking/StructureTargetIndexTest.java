package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StructureTargetIndexTest {
    private final UUID world = UUID.randomUUID();
    private final TrackingPosition viewer = new TrackingPosition(world, TrackingWorldKind.NETHER, 100, 70, 100);
    private final TrackingTargetId fortress = TrackingTargetId.NETHER_FORTRESS;

    @Test void confirmedCurrentFortressOverridesTheStaleTarget719BlocksAway() {
        var index = new StructureTargetIndex(8);
        var old = new PortalPoint(819, 70, 100);
        index.recordPoint(world, fortress, old);
        index.recordRegion(world, fortress, new StructureTargetIndex.Bounds(80, 60, 80, 130, 90, 130),
                List.of(new StructureTargetIndex.Bounds(90, 65, 90, 110, 76, 110)));
        assertEquals(viewer, index.nearest(fortress, viewer, old, 4096).orElseThrow());
    }

    @Test void closerKnownPointsAreComparedFromCurrentPositionWithoutCrossWorldOrTargetLeaks() {
        var index = new StructureTargetIndex(8);
        index.recordPoint(world, fortress, new PortalPoint(120, 70, 100));
        index.recordPoint(UUID.randomUUID(), fortress, new PortalPoint(101, 70, 100));
        index.recordPoint(world, TrackingTargetId.BASTION_REMNANT, new PortalPoint(100, 70, 100));
        var result = index.nearest(fortress, viewer, new PortalPoint(819, 70, 100), 4096).orElseThrow();
        assertEquals(120, result.x());
        assertEquals(70, result.y());
    }

    @Test void distanceUsesActualPiecesNotEmptySpaceInTheOverallStructureBox() {
        var index = new StructureTargetIndex(8);
        index.recordRegion(world, fortress, new StructureTargetIndex.Bounds(0, 0, 0, 200, 100, 200),
                List.of(new StructureTargetIndex.Bounds(0, 60, 0, 20, 80, 20),
                        new StructureTargetIndex.Bounds(180, 60, 180, 200, 80, 200)));
        var result = index.nearest(fortress, viewer, null, 4096).orElseThrow();
        assertNotEquals(viewer, result);
        assertEquals(20, result.x());
        assertEquals(20, result.z());
    }

    @Test void boundedIndexAndWorldCleanupDoNotRetainOldGeometry() {
        var index = new StructureTargetIndex(2);
        index.recordPoint(world, fortress, new PortalPoint(101, 70, 100));
        index.recordPoint(world, fortress, new PortalPoint(102, 70, 100));
        index.recordPoint(world, fortress, new PortalPoint(103, 70, 100));
        assertEquals(2, index.size());
        assertEquals(102, index.nearest(fortress, viewer, null, 4096).orElseThrow().x());
        index.unload(world);
        assertTrue(index.nearest(fortress, viewer, null, 4096).isEmpty());
    }

    @Test void fortressPrefersItsNearestSpawnerEvenWhenTheViewerIsInsideTheStructure() {
        var index = new StructureTargetIndex(8);
        var box = new StructureTargetIndex.Bounds(80, 60, 80, 160, 90, 160);
        index.recordRegion(world, fortress, box, List.of(box),
                List.of(new PortalPoint(150, 70, 100), new PortalPoint(110, 70, 100)));
        var nearest = index.nearest(fortress, viewer, new PortalPoint(819, 70, 100), 4096).orElseThrow();
        assertEquals(110, nearest.x());
        assertEquals(70, nearest.y());
    }

    @Test void aSpawnerInAnotherFortressCannotReplaceTheSelectedFortressFallback() {
        var index = new StructureTargetIndex(8);
        var near = new StructureTargetIndex.Bounds(95, 65, 95, 105, 80, 105);
        var far = new StructureTargetIndex.Bounds(200, 65, 95, 210, 80, 105);
        index.recordRegion(world, fortress, near, List.of(near), List.of());
        index.recordRegion(world, fortress, far, List.of(far), List.of(new PortalPoint(205, 70, 100)));
        assertEquals(viewer, index.nearest(fortress, viewer, null, 4096).orElseThrow());
        index.recordRegion(world, fortress, near, List.of(near), List.of(new PortalPoint(101, 70, 100)));
        index.pruneSpawners((id, point) -> point.x() != 101);
        assertEquals(viewer, index.nearest(fortress, viewer, null, 4096).orElseThrow());
    }

    @Test void geometryRefreshKeepsConfirmedSpawnersUntilActualRevalidationAndNeverLeaksAcrossTargets() {
        var index = new StructureTargetIndex(8);
        var box = new StructureTargetIndex.Bounds(80, 60, 80, 160, 90, 160);
        index.recordRegion(world, fortress, box, List.of(box), List.of(new PortalPoint(110, 70, 100)));
        index.recordRegion(world, fortress, box, List.of(box));
        assertEquals(110, index.nearest(fortress, viewer, null, 4096).orElseThrow().x());
        assertTrue(index.nearest(TrackingTargetId.BASTION_REMNANT, viewer, null, 4096).isEmpty());
        index.recordRegion(world, fortress, box, List.of(box), List.of());
        assertEquals(viewer, index.nearest(fortress, viewer, null, 4096).orElseThrow());
    }

    @Test void nativeReferencePointCanUseItsObservedFortressSpawners() {
        var index = new StructureTargetIndex(8);
        var overall = new StructureTargetIndex.Bounds(80, 60, 80, 160, 90, 160);
        var distantPiece = new StructureTargetIndex.Bounds(140, 60, 140, 160, 90, 160);
        index.recordRegion(world, fortress, overall, List.of(distantPiece), List.of(new PortalPoint(150, 70, 150)));
        assertEquals(150, index.nearest(fortress, viewer, new PortalPoint(100, 70, 100), 4096).orElseThrow().x());
    }

    @Test void spawnersUseThreeDimensionalDistanceAndCannotComeFromAnotherFortressOrOutsideRadius() {
        var index = new StructureTargetIndex(8);
        var selected = new StructureTargetIndex.Bounds(95, 60, 95, 150, 150, 105);
        var other = new StructureTargetIndex.Bounds(110, 60, 95, 115, 80, 105);
        index.recordRegion(world, fortress, selected, List.of(selected),
                List.of(new PortalPoint(101, 140, 100), new PortalPoint(140, 70, 100)));
        index.recordRegion(world, fortress, other, List.of(other), List.of(new PortalPoint(111, 70, 100)));
        assertEquals(140, index.nearest(fortress, viewer, null, 4096).orElseThrow().x());
        assertEquals(viewer, index.nearest(fortress, viewer, null, 20).orElseThrow());
    }
}
