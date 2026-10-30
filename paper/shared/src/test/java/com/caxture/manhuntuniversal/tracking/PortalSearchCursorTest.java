package com.caxture.manhuntuniversal.tracking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortalSearchCursorTest {
    @Test void matchesBruteForceBlocksWithoutDuplicatesAndKeepsASafeRemainingBound() {
        var random = new Random(0x504f5254414cL);
        for (int trial = 0; trial < 120; trial++) {
            var origin = new PortalPoint(random.nextInt(161) - 80, random.nextInt(500) - 250,
                    random.nextInt(161) - 80);
            int radius = random.nextInt(64) + 1;
            Map<PortalSearchCursor.Chunk, Double> remaining = bruteForceChunks(origin, radius);
            var cursor = new PortalSearchCursor(origin, radius);
            Set<PortalSearchCursor.Chunk> returned = new HashSet<>();
            int lastRing = -1;
            assertSafeBound(cursor, remaining);
            PortalSearchCursor.Chunk chunk;
            while ((chunk = cursor.next()) != null) {
                assertTrue(returned.add(chunk), "duplicate chunk " + chunk);
                assertNotNull(remaining.remove(chunk), "chunk outside radius " + chunk);
                int ring = Math.max(Math.abs(chunk.x() - Math.floorDiv(origin.x(), 16)),
                        Math.abs(chunk.z() - Math.floorDiv(origin.z(), 16)));
                assertTrue(ring >= lastRing, "returned to an earlier ring");
                lastRing = ring;
                assertSafeBound(cursor, remaining);
            }
            assertTrue(remaining.isEmpty(), "missed chunks: " + remaining.keySet());
            assertEquals(Double.POSITIVE_INFINITY, cursor.minimumRemainingDistanceSquared());
            assertNull(cursor.next());
        }
    }

    @Test void usesIntegerBlockEdgesAndFloorDivisionAtNegativeCoordinates() {
        assertEquals(2.0, PortalSearchCursor.distanceSquaredToChunk(
                new PortalPoint(0, 100, 0), new PortalSearchCursor.Chunk(-1, -1)));
        assertEquals(2.0, PortalSearchCursor.distanceSquaredToChunk(
                new PortalPoint(-16, -100, -16), new PortalSearchCursor.Chunk(-2, -2)));
        assertEquals(2.0, PortalSearchCursor.distanceSquaredToChunk(
                new PortalPoint(-1, 0, -1), new PortalSearchCursor.Chunk(0, 0)));
        for (int coordinate : new int[]{-33, -32, -17, -16, -1, 0, 15, 16, 31, 32}) {
            var cursor = new PortalSearchCursor(new PortalPoint(coordinate, 0, coordinate), 1);
            assertEquals(new PortalSearchCursor.Chunk(Math.floorDiv(coordinate, 16),
                    Math.floorDiv(coordinate, 16)), cursor.next());
        }
    }

    @Test void includesChunksAtTheExactRadiusAndExcludesTheNextBlock() {
        var origin = new PortalPoint(0, 64, 0);
        Set<PortalSearchCursor.Chunk> chunks = collect(new PortalSearchCursor(origin, 16));
        assertTrue(chunks.contains(new PortalSearchCursor.Chunk(1, 0)));
        assertTrue(chunks.contains(new PortalSearchCursor.Chunk(-1, 0)));
        assertFalse(chunks.contains(new PortalSearchCursor.Chunk(-2, 0)));
        assertFalse(chunks.contains(new PortalSearchCursor.Chunk(1, 1)));
    }

    @Test void advancesTheBoundAfterCompleteRingsSoNearbyMatchesCanFinishEarly() {
        var cursor = new PortalSearchCursor(new PortalPoint(8, 64, 8), 256);
        assertEquals(0.0, cursor.minimumRemainingDistanceSquared());
        assertEquals(new PortalSearchCursor.Chunk(0, 0), cursor.next());
        assertEquals(64.0, cursor.minimumRemainingDistanceSquared());
        for (int i = 0; i < 7; i++) {
            assertNotNull(cursor.next());
            assertEquals(64.0, cursor.minimumRemainingDistanceSquared());
        }
        assertNotNull(cursor.next());
        assertEquals(576.0, cursor.minimumRemainingDistanceSquared());
        assertTrue(400.0 <= cursor.minimumRemainingDistanceSquared(),
                "a nearby match should prune all later rings");
    }

    @Test void largeRadiusStartsLazilyAndDoesNotOverflowDistanceArithmetic() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            var cursor = new PortalSearchCursor(new PortalPoint(-30_000_000, 64, 30_000_000), 16_384);
            for (int i = 0; i < 64; i++) {
                assertNotNull(cursor.next());
            }
        });
        assertEquals(4_293_001_441.0, PortalSearchCursor.distanceSquaredToChunk(
                new PortalPoint(0, 0, 0), new PortalSearchCursor.Chunk(-4096, 0)));
    }

    @Test void rejectsNonPositiveRadius() {
        var origin = new PortalPoint(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new PortalSearchCursor(origin, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortalSearchCursor(origin, -1));
    }

    private static Set<PortalSearchCursor.Chunk> collect(PortalSearchCursor cursor) {
        Set<PortalSearchCursor.Chunk> chunks = new HashSet<>();
        PortalSearchCursor.Chunk chunk;
        while ((chunk = cursor.next()) != null) {
            assertTrue(chunks.add(chunk));
        }
        return chunks;
    }

    private static Map<PortalSearchCursor.Chunk, Double> bruteForceChunks(PortalPoint origin, int radius) {
        Map<PortalSearchCursor.Chunk, Double> chunks = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = (double) dx * dx + (double) dz * dz;
                if (distance <= (double) radius * radius) {
                    var chunk = new PortalSearchCursor.Chunk(Math.floorDiv(origin.x() + dx, 16),
                            Math.floorDiv(origin.z() + dz, 16));
                    chunks.merge(chunk, distance, Math::min);
                }
            }
        }
        return chunks;
    }

    private static void assertSafeBound(PortalSearchCursor cursor,
                                       Map<PortalSearchCursor.Chunk, Double> remaining) {
        double nearestRemaining = remaining.values().stream().mapToDouble(Double::doubleValue)
                .min().orElse(Double.POSITIVE_INFINITY);
        assertTrue(cursor.minimumRemainingDistanceSquared() <= nearestRemaining,
                () -> "unsafe lower bound " + cursor.minimumRemainingDistanceSquared()
                        + " > " + nearestRemaining);
    }
}
