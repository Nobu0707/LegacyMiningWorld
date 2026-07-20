package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LegacyOrePlannerTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private static final List<ChunkPosition> GOLDEN_CHUNKS = List.of(
            new ChunkPosition(0, 0),
            new ChunkPosition(-1, 0),
            new ChunkPosition(0, -1),
            new ChunkPosition(-1, -1));
    private final LegacyOrePlanner planner = new LegacyOrePlanner();

    @Test
    void emitsStablePlansForFourPositiveAndNegativeTargets() {
        Map<LegacyOreMaterial, Long> totals = new EnumMap<>(LegacyOreMaterial.class);
        for (ChunkPosition chunk : GOLDEN_CHUNKS) {
            List<LegacyOrePlacement> placements = planner.plan(FIXED_SEED, chunk.x(), chunk.z());
            Map<LegacyOreMaterial, Long> counts = OreTestSupport.materialCounts(placements);
            counts.forEach((material, count) -> totals.merge(material, count, Long::sum));
            System.out.printf(
                    "ORE_PLAN_PROBE seed=%d target=%d,%d count=%d checksum=%d bounds=%s materials=%s first=%s last=%s%n",
                    FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    placements.size(),
                    OreTestSupport.placementChecksum(placements),
                    OreTestSupport.bounds(placements),
                    counts,
                    placements.getFirst(),
                    placements.getLast());
            assertOwnedByTarget(placements, chunk.x(), chunk.z());
            assertStableOrder(placements);
            assertEquals(placements, planner.plan(FIXED_SEED, chunk.x(), chunk.z()));
            assertEquals(golden(chunk), new PlanResult(
                    placements.size(),
                    OreTestSupport.placementChecksum(placements),
                    OreTestSupport.bounds(placements),
                    counts,
                    placements.getFirst(),
                    placements.getLast()));
        }
        for (LegacyOreMaterial material : LegacyOreMaterial.values()) {
            assertTrue(totals.getOrDefault(material, 0L) > 0L, material::name);
        }
        System.out.printf("ORE_FOUR_CHUNK_MATERIAL_TOTALS seed=%d materials=%s%n", FIXED_SEED, totals);
    }

    @Test
    void streamingAndInspectionApisHaveTheSameStableOrder() {
        List<LegacyOrePlacement> inspected = planner.plan(FIXED_SEED, 0, 0);
        List<LegacyOrePlacement> streamed = new ArrayList<>();
        planner.plan(FIXED_SEED, 0, 0, streamed::add);
        assertEquals(inspected, streamed);
        assertThrows(NullPointerException.class,
                () -> planner.plan(FIXED_SEED, 0, 0, null));
    }

    @Test
    void handlesWorldBorderScaleCoordinatesAndRejectsIntegerBlockOverflow() {
        assertOwnedByTarget(planner.plan(FIXED_SEED, 1_875_000, -1_875_000),
                1_875_000, -1_875_000);
        assertThrows(ArithmeticException.class,
                () -> planner.plan(FIXED_SEED, Integer.MAX_VALUE, 0));
        assertThrows(ArithmeticException.class,
                () -> planner.plan(FIXED_SEED, 0, Integer.MIN_VALUE));
    }

    @Test
    void containsDeterministicallyOrderedOverlapsWithoutResolvingThemInThePurePlanner() {
        Map<BlockCoordinate, List<LegacyOrePlacement>> byCoordinate = new HashMap<>();
        for (ChunkPosition chunk : GOLDEN_CHUNKS) {
            for (LegacyOrePlacement placement : planner.plan(FIXED_SEED, chunk.x(), chunk.z())) {
                byCoordinate.computeIfAbsent(
                        new BlockCoordinate(placement.x(), placement.y(), placement.z()),
                        ignored -> new ArrayList<>())
                        .add(placement);
            }
        }
        List<LegacyOrePlacement> overlap = byCoordinate.values().stream()
                .filter(placements -> placements.stream()
                        .map(LegacyOrePlacement::material)
                        .distinct()
                        .count() > 1)
                .findFirst()
                .orElseThrow();
        assertStableOrder(overlap);
        assertTrue(overlap.getFirst().featureOrder() < overlap.getLast().featureOrder());
    }

    @Test
    void reusesOnePlannerConcurrentlyWithoutMutableSharedState() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<PlanResult> task = () -> {
                List<LegacyOrePlacement> placements = planner.plan(FIXED_SEED, -1, 0);
                return new PlanResult(
                        placements.size(),
                        OreTestSupport.placementChecksum(placements),
                        OreTestSupport.bounds(placements),
                        OreTestSupport.materialCounts(placements),
                        placements.getFirst(),
                        placements.getLast());
            };
            List<Callable<PlanResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(task);
            }
            PlanResult expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        for (Field field : LegacyOrePlanner.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
        }
    }

    private static void assertOwnedByTarget(
            List<LegacyOrePlacement> placements, int targetChunkX, int targetChunkZ) {
        int minimumX = Math.multiplyExact(targetChunkX, 16);
        int minimumZ = Math.multiplyExact(targetChunkZ, 16);
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

    private static void assertStableOrder(List<LegacyOrePlacement> placements) {
        Comparator<LegacyOrePlacement> stableOrder = Comparator
                .comparingInt(LegacyOrePlacement::sourceChunkZ)
                .thenComparingInt(LegacyOrePlacement::sourceChunkX)
                .thenComparingInt(LegacyOrePlacement::featureOrder)
                .thenComparingInt(LegacyOrePlacement::attemptIndex)
                .thenComparingInt(LegacyOrePlacement::veinSequence);
        for (int index = 1; index < placements.size(); index++) {
            assertTrue(stableOrder.compare(placements.get(index - 1), placements.get(index)) <= 0);
        }
    }

    private static PlanResult golden(ChunkPosition chunk) {
        if (chunk.x() == 0 && chunk.z() == 0) {
            return new PlanResult(
                    613,
                    -6_214_814_787_450_030_649L,
                    new OreTestSupport.Bounds(0, 15, -1, 125, 0, 15),
                    Map.of(
                            LegacyOreMaterial.COAL_ORE, 431L,
                            LegacyOreMaterial.IRON_ORE, 111L,
                            LegacyOreMaterial.GOLD_ORE, 14L,
                            LegacyOreMaterial.REDSTONE_ORE, 49L,
                            LegacyOreMaterial.DIAMOND_ORE, 2L,
                            LegacyOreMaterial.LAPIS_ORE, 6L),
                    new LegacyOrePlacement(
                            1, 43, 0, LegacyOreMaterial.COAL_ORE, 0, -1, 5, 3, 21),
                    new LegacyOrePlacement(
                            15, 124, 14, LegacyOreMaterial.COAL_ORE, 1, 1, 5, 19, 19));
        }
        if (chunk.x() == -1 && chunk.z() == 0) {
            return new PlanResult(
                    607,
                    1_707_629_220_185_779_456L,
                    new OreTestSupport.Bounds(-16, -1, -2, 127, 0, 15),
                    Map.of(
                            LegacyOreMaterial.COAL_ORE, 435L,
                            LegacyOreMaterial.IRON_ORE, 114L,
                            LegacyOreMaterial.GOLD_ORE, 16L,
                            LegacyOreMaterial.REDSTONE_ORE, 34L,
                            LegacyOreMaterial.DIAMOND_ORE, 4L,
                            LegacyOreMaterial.LAPIS_ORE, 4L),
                    new LegacyOrePlacement(
                            -6, 8, 0, LegacyOreMaterial.COAL_ORE, -1, -1, 5, 11, 16),
                    new LegacyOrePlacement(
                            -15, 102, 14, LegacyOreMaterial.COAL_ORE, -1, 1, 5, 19, 15));
        }
        if (chunk.x() == 0 && chunk.z() == -1) {
            return new PlanResult(
                    626,
                    7_380_527_893_828_012_375L,
                    new OreTestSupport.Bounds(0, 15, -3, 124, -16, -1),
                    Map.of(
                            LegacyOreMaterial.COAL_ORE, 401L,
                            LegacyOreMaterial.IRON_ORE, 154L,
                            LegacyOreMaterial.GOLD_ORE, 12L,
                            LegacyOreMaterial.REDSTONE_ORE, 46L,
                            LegacyOreMaterial.DIAMOND_ORE, 8L,
                            LegacyOreMaterial.LAPIS_ORE, 5L),
                    new LegacyOrePlacement(
                            15, 19, -4, LegacyOreMaterial.COAL_ORE, 0, -1, 5, 0, 0),
                    new LegacyOrePlacement(
                            15, 10, -1, LegacyOreMaterial.IRON_ORE, 1, 0, 6, 17, 1));
        }
        if (chunk.x() == -1 && chunk.z() == -1) {
            return new PlanResult(
                    604,
                    3_576_546_210_841_447_872L,
                    new OreTestSupport.Bounds(-16, -1, -3, 125, -16, -1),
                    Map.of(
                            LegacyOreMaterial.COAL_ORE, 418L,
                            LegacyOreMaterial.IRON_ORE, 127L,
                            LegacyOreMaterial.GOLD_ORE, 10L,
                            LegacyOreMaterial.REDSTONE_ORE, 38L,
                            LegacyOreMaterial.DIAMOND_ORE, 5L,
                            LegacyOreMaterial.LAPIS_ORE, 6L),
                    new LegacyOrePlacement(
                            -2, 84, -16, LegacyOreMaterial.COAL_ORE, -1, -2, 5, 16, 17),
                    new LegacyOrePlacement(
                            -1, 47, -1, LegacyOreMaterial.IRON_ORE, 0, 0, 6, 18, 4));
        }
        throw new AssertionError("unexpected golden chunk: " + chunk);
    }

    private record ChunkPosition(int x, int z) {
    }

    private record BlockCoordinate(int x, int y, int z) {
    }

    private record PlanResult(
            int count,
            long checksum,
            OreTestSupport.Bounds bounds,
            Map<LegacyOreMaterial, Long> materialCounts,
            LegacyOrePlacement first,
            LegacyOrePlacement last) {
    }
}
