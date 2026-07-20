package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LegacyGeologyPlannerTest {
    private static final long GOLDEN_WORLD_SEED = 1_165_2021L;
    private final LegacyGeologyPlanner planner = new LegacyGeologyPlanner();

    @Test
    void ownsOnlyCoordinatesInsidePositiveNegativeAndLargeTargets() {
        assertOwnedByTarget(0, 0);
        assertOwnedByTarget(1, 0);
        assertOwnedByTarget(-1, 0);
        assertOwnedByTarget(0, -1);
        assertOwnedByTarget(17_000, -23_000);
    }

    @Test
    void freezesPlannerCountChecksumAndOrder() {
        List<LegacyPlacement> placements = planner.plan(GOLDEN_WORLD_SEED, 0, 0);
        System.out.printf(
                "PLANNER_PROBE seed=%d target=0,0 count=%d checksum=%d first=%s last=%s%n",
                GOLDEN_WORLD_SEED,
                placements.size(),
                GeologyTestSupport.placementChecksum(placements),
                placements.getFirst(),
                placements.getLast());
        assertEquals(5_564, placements.size());
        assertEquals(-4_572_519_745_665_027_215L,
                GeologyTestSupport.placementChecksum(placements));
        assertEquals(
                new LegacyPlacement(0, 6, 0, LegacyGeologyMaterial.DIORITE, -1, -1, 3, 7, 100),
                placements.getFirst());
        assertEquals(
                new LegacyPlacement(15, 49, 13, LegacyGeologyMaterial.DIORITE, 1, 1, 3, 5, 105),
                placements.getLast());
        assertEquals(placements, planner.plan(GOLDEN_WORLD_SEED, 0, 0));

        Comparator<LegacyPlacement> stableOrder = Comparator
                .comparingInt(LegacyPlacement::sourceChunkZ)
                .thenComparingInt(LegacyPlacement::sourceChunkX)
                .thenComparingInt(LegacyPlacement::featureOrder)
                .thenComparingInt(LegacyPlacement::attemptIndex)
                .thenComparingInt(LegacyPlacement::veinSequence);
        for (int index = 1; index < placements.size(); index++) {
            assertTrue(stableOrder.compare(placements.get(index - 1), placements.get(index)) <= 0);
        }
    }

    @Test
    void targetProcessingOrderDoesNotChangeEitherPlan() {
        List<LegacyPlacement> zeroFirst = planner.plan(GOLDEN_WORLD_SEED, 0, 0);
        List<LegacyPlacement> oneSecond = planner.plan(GOLDEN_WORLD_SEED, 1, 0);
        List<LegacyPlacement> oneFirst = planner.plan(GOLDEN_WORLD_SEED, 1, 0);
        List<LegacyPlacement> zeroSecond = planner.plan(GOLDEN_WORLD_SEED, 0, 0);
        assertEquals(zeroFirst, zeroSecond);
        assertEquals(oneSecond, oneFirst);
    }

    @Test
    void consumesExactlyTheConfiguredOriginsInOfficialPlacementOrder() {
        int[][] expectedFirstOrigins = {
            {8, 1, 56},
            {8, 4, 181},
            {8, 9, 72},
            {8, 14, 77},
            {8, 3, 55}
        };
        int attemptCount = 0;
        for (LegacyGeologyFeature feature : LegacyGeologySettings.FEATURES) {
            Random random = LegacyDecorationSeed.featureRandom(GOLDEN_WORLD_SEED, -3, 4, feature);
            for (int attempt = 0; attempt < feature.attempts(); attempt++) {
                int localX = random.nextInt(16);
                int localZ = random.nextInt(16);
                int originY = feature.originMinY()
                        + random.nextInt(feature.originMaxYExclusive() - feature.originMinY());
                assertTrue(localX >= 0 && localX < 16);
                assertTrue(localZ >= 0 && localZ < 16);
                assertTrue(originY >= feature.originMinY());
                assertTrue(originY < feature.originMaxYExclusive());
                if (attempt == 0) {
                    assertEquals(expectedFirstOrigins[feature.stableOrder()][0], localX);
                    assertEquals(expectedFirstOrigins[feature.stableOrder()][1], localZ);
                    assertEquals(expectedFirstOrigins[feature.stableOrder()][2], originY);
                }
                new LegacyVeinGenerator().generate(
                        random,
                        new LegacyBlockPosition((-3 << 4) + localX, originY, (4 << 4) + localZ),
                        33,
                        (x, y, z, sequence) -> {});
                attemptCount++;
            }
        }
        assertEquals(48, attemptCount);
    }

    @Test
    void keepsOriginsInsideTheirExplicitRangesAcrossSourceChunks() {
        int[][] sourceChunks = {
            {0, 0}, {1, 1}, {-1, -1}, {-7, 9}, {17, -23}, {10_000, -10_000}
        };
        int attempts = 0;
        for (int[] sourceChunk : sourceChunks) {
            for (LegacyGeologyFeature feature : LegacyGeologySettings.FEATURES) {
                Random random = LegacyDecorationSeed.featureRandom(
                        -4_294_967_296L, sourceChunk[0], sourceChunk[1], feature);
                for (int attempt = 0; attempt < feature.attempts(); attempt++) {
                    int localX = random.nextInt(16);
                    int localZ = random.nextInt(16);
                    int originY = feature.originMinY()
                            + random.nextInt(feature.originMaxYExclusive() - feature.originMinY());
                    assertTrue(localX >= 0 && localX < 16);
                    assertTrue(localZ >= 0 && localZ < 16);
                    assertTrue(originY >= 0 && originY < feature.originMaxYExclusive());
                    new LegacyVeinGenerator().generate(
                            random,
                            new LegacyBlockPosition(
                                    (sourceChunk[0] << 4) + localX,
                                    originY,
                                    (sourceChunk[1] << 4) + localZ),
                            33,
                            (x, y, z, sequence) -> {});
                    attempts++;
                }
            }
        }
        assertEquals(sourceChunks.length * 48, attempts);
    }

    @Test
    void isSafeToReuseConcurrentlyWithoutMutableStaticState() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<List<LegacyPlacement>> task = () -> planner.plan(GOLDEN_WORLD_SEED, -1, 1);
            List<Callable<List<LegacyPlacement>>> tasks = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                tasks.add(task);
            }
            List<LegacyPlacement> expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        for (Field field : LegacyGeologyPlanner.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
        }
    }

    private void assertOwnedByTarget(int targetChunkX, int targetChunkZ) {
        int minimumX = targetChunkX << 4;
        int minimumZ = targetChunkZ << 4;
        List<LegacyPlacement> placements = planner.plan(
                GOLDEN_WORLD_SEED, targetChunkX, targetChunkZ);
        assertFalse(placements.isEmpty());
        assertTrue(placements.stream().allMatch(placement ->
                placement.x() >= minimumX && placement.x() < minimumX + 16));
        assertTrue(placements.stream().allMatch(placement ->
                placement.z() >= minimumZ && placement.z() < minimumZ + 16));
        assertTrue(placements.stream().anyMatch(placement -> placement.x() == minimumX));
        assertTrue(placements.stream().anyMatch(placement -> placement.x() == minimumX + 15));
        assertTrue(placements.stream().anyMatch(placement -> placement.z() == minimumZ));
        assertTrue(placements.stream().anyMatch(placement -> placement.z() == minimumZ + 15));
    }
}
