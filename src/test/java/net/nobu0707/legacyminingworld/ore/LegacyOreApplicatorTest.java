package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.nobu0707.legacyminingworld.geology.LegacyBlockKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class LegacyOreApplicatorTest {
    private final LegacyOreApplicator applicator = new LegacyOreApplicator();

    @Test
    void replacesOnlyTheFourNaturalStoneKinds() {
        for (LegacyBlockKind initial : LegacyBlockKind.values()) {
            TrackingAccess access = new TrackingAccess();
            access.put(1, 5, 1, initial);
            var summary = applicator.applyPlacements(
                    List.of(placement(1, 5, 1, LegacyOreMaterial.COAL_ORE)),
                    0,
                    0,
                    -64,
                    320,
                    access);
            boolean replaceable = switch (initial) {
                case STONE, GRANITE, DIORITE, ANDESITE -> true;
                default -> false;
            };
            assertEquals(replaceable ? 1 : 0, summary.applied(), initial::name);
            assertEquals(replaceable ? 0 : 1, summary.skippedNotReplaceable(), initial::name);
            assertEquals(
                    replaceable ? LegacyBlockKind.COAL_ORE : initial,
                    access.kindAt(1, 5, 1),
                    initial::name);
        }
    }

    @Test
    void enforcesWorldLegacyTargetAndRegionBoundariesBeforeReads() {
        TrackingAccess access = new TrackingAccess();
        for (int x = 0; x <= 15; x++) {
            access.put(x, 1, 0, LegacyBlockKind.STONE);
        }
        access.put(2, 0, 0, LegacyBlockKind.BEDROCK);
        access.put(1, -1, 0, LegacyBlockKind.STONE);
        access.put(3, 1, 0, LegacyBlockKind.STONE);
        access.put(4, 4, 0, LegacyBlockKind.STONE);
        access.put(5, 5, 0, LegacyBlockKind.STONE);
        access.put(6, 67, 0, LegacyBlockKind.STONE);
        access.put(8, 68, 0, LegacyBlockKind.STONE);
        access.put(9, 69, 0, LegacyBlockKind.STONE);
        access.put(10, 70, 0, LegacyBlockKind.STONE);
        access.deny(7, 10, 0);
        List<LegacyOrePlacement> placements = List.of(
                placement(0, -65, 0, LegacyOreMaterial.COAL_ORE),
                placement(1, -1, 0, LegacyOreMaterial.COAL_ORE),
                placement(2, 0, 0, LegacyOreMaterial.COAL_ORE),
                placement(3, 1, 0, LegacyOreMaterial.COAL_ORE),
                placement(4, 4, 0, LegacyOreMaterial.COAL_ORE),
                placement(5, 5, 0, LegacyOreMaterial.COAL_ORE),
                placement(6, 67, 0, LegacyOreMaterial.COAL_ORE),
                placement(8, 68, 0, LegacyOreMaterial.COAL_ORE),
                placement(9, 69, 0, LegacyOreMaterial.COAL_ORE),
                placement(10, 70, 0, LegacyOreMaterial.COAL_ORE),
                placement(11, 320, 0, LegacyOreMaterial.COAL_ORE),
                placement(16, 5, 0, LegacyOreMaterial.COAL_ORE),
                placement(0, 5, 16, LegacyOreMaterial.COAL_ORE),
                placement(7, 10, 0, LegacyOreMaterial.COAL_ORE));

        var summary = applicator.applyPlacements(placements, 0, 0, -64, 320, access);

        assertEquals(14, summary.planned());
        assertEquals(4, summary.applied());
        assertEquals(2, summary.skippedByWorldHeight());
        assertEquals(4, summary.skippedOutsideLegacyOreLayer());
        assertEquals(2, summary.skippedOutsideTargetChunk());
        assertEquals(1, summary.skippedOutOfRegion());
        assertEquals(1, summary.skippedNotReplaceable());
        assertEquals(5, access.getCalls());
        assertEquals(4, access.setCalls());
        assertEquals(LegacyBlockKind.STONE, access.kindAt(1, -1, 0));
        assertEquals(LegacyBlockKind.BEDROCK, access.kindAt(2, 0, 0));
        assertEquals(LegacyBlockKind.COAL_ORE, access.kindAt(3, 1, 0));
        assertEquals(LegacyBlockKind.COAL_ORE, access.kindAt(4, 4, 0));
        assertEquals(LegacyBlockKind.COAL_ORE, access.kindAt(5, 5, 0));
        assertEquals(LegacyBlockKind.COAL_ORE, access.kindAt(6, 67, 0));
        assertEquals(LegacyBlockKind.STONE, access.kindAt(8, 68, 0));
        assertEquals(LegacyBlockKind.STONE, access.kindAt(9, 69, 0));
        assertEquals(LegacyBlockKind.STONE, access.kindAt(10, 70, 0));
    }

    @Test
    void honorsAllPositiveAndNegativeChunkEdges() {
        assertEdgeApplication(0, 0, List.of(
                placement(15, 5, 15, LegacyOreMaterial.IRON_ORE),
                placement(16, 5, 15, LegacyOreMaterial.IRON_ORE),
                placement(15, 5, 16, LegacyOreMaterial.IRON_ORE)));
        assertEdgeApplication(-1, -1, List.of(
                placement(-1, 5, -1, LegacyOreMaterial.IRON_ORE),
                placement(0, 5, -1, LegacyOreMaterial.IRON_ORE),
                placement(-1, 5, 0, LegacyOreMaterial.IRON_ORE)));
    }

    @Test
    void stableReadBeforeWriteMakesTheFirstOreWin() {
        List<LegacyOrePlacement> allOres = List.of(
                placement(1, 11, 1, LegacyOreMaterial.COAL_ORE),
                placement(1, 11, 1, LegacyOreMaterial.IRON_ORE),
                placement(1, 11, 1, LegacyOreMaterial.GOLD_ORE),
                placement(1, 11, 1, LegacyOreMaterial.REDSTONE_ORE),
                placement(1, 11, 1, LegacyOreMaterial.DIAMOND_ORE),
                placement(1, 11, 1, LegacyOreMaterial.LAPIS_ORE));
        for (LegacyBlockKind initial : List.of(
                LegacyBlockKind.STONE,
                LegacyBlockKind.GRANITE,
                LegacyBlockKind.DIRT,
                LegacyBlockKind.DIAMOND_ORE)) {
            TrackingAccess access = new TrackingAccess();
            access.put(1, 11, 1, initial);
            var summary = applicator.applyPlacements(allOres, 0, 0, -64, 320, access);
            boolean firstApplies = initial == LegacyBlockKind.STONE
                    || initial == LegacyBlockKind.GRANITE;
            assertEquals(firstApplies ? LegacyBlockKind.COAL_ORE : initial,
                    access.kindAt(1, 11, 1));
            assertEquals(firstApplies ? 1 : 0, summary.applied());
            assertEquals(firstApplies ? 5 : 6, summary.skippedNotReplaceable());
        }
    }

    @Test
    void reusesOneApplicatorConcurrentlyWithoutSharedMutableState() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<ConcurrentResult> task = () -> {
                AllStoneAccess access = new AllStoneAccess();
                var summary = applicator.apply(1_165_2021L, 0, 0, -64, 320, access);
                return new ConcurrentResult(summary, access.counts(), access.checksum());
            };
            List<Callable<ConcurrentResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(task);
            }
            ConcurrentResult expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertEdgeApplication(
            int targetChunkX, int targetChunkZ, List<LegacyOrePlacement> placements) {
        TrackingAccess access = new TrackingAccess();
        for (LegacyOrePlacement placement : placements) {
            access.put(placement.x(), placement.y(), placement.z(), LegacyBlockKind.STONE);
        }
        var summary = applicator.applyPlacements(
                placements, targetChunkX, targetChunkZ, -64, 320, access);
        assertEquals(1, summary.applied());
        assertEquals(2, summary.skippedOutsideTargetChunk());
        assertEquals(1, access.getCalls());
        assertEquals(1, access.setCalls());
    }

    private static LegacyOrePlacement placement(
            int x, int y, int z, LegacyOreMaterial material) {
        return new LegacyOrePlacement(x, y, z, material, 0, 0, 0, 0, 0);
    }

    private static final class TrackingAccess implements LegacyOreBlockAccess {
        private final Map<Position, LegacyBlockKind> blocks = new HashMap<>();
        private final Map<Position, Boolean> denied = new HashMap<>();
        private int getCalls;
        private int setCalls;

        void put(int x, int y, int z, LegacyBlockKind kind) {
            blocks.put(new Position(x, y, z), kind);
        }

        void deny(int x, int y, int z) {
            denied.put(new Position(x, y, z), true);
        }

        LegacyBlockKind kindAt(int x, int y, int z) {
            return blocks.get(new Position(x, y, z));
        }

        int getCalls() {
            return getCalls;
        }

        int setCalls() {
            return setCalls;
        }

        @Override
        public boolean isAccessible(int x, int y, int z) {
            Position position = new Position(x, y, z);
            return blocks.containsKey(position) && !denied.getOrDefault(position, false);
        }

        @Override
        public LegacyBlockKind getBlockKind(int x, int y, int z) {
            getCalls++;
            return kindAt(x, y, z);
        }

        @Override
        public void setMaterial(int x, int y, int z, LegacyOreMaterial material) {
            setCalls++;
            blocks.put(new Position(x, y, z), ORE_KINDS.get(material));
        }

        private static final Map<LegacyOreMaterial, LegacyBlockKind> ORE_KINDS =
                new EnumMap<>(LegacyOreMaterial.class);

        static {
            ORE_KINDS.put(LegacyOreMaterial.COAL_ORE, LegacyBlockKind.COAL_ORE);
            ORE_KINDS.put(LegacyOreMaterial.IRON_ORE, LegacyBlockKind.IRON_ORE);
            ORE_KINDS.put(LegacyOreMaterial.GOLD_ORE, LegacyBlockKind.GOLD_ORE);
            ORE_KINDS.put(LegacyOreMaterial.REDSTONE_ORE, LegacyBlockKind.REDSTONE_ORE);
            ORE_KINDS.put(LegacyOreMaterial.DIAMOND_ORE, LegacyBlockKind.DIAMOND_ORE);
            ORE_KINDS.put(LegacyOreMaterial.LAPIS_ORE, LegacyBlockKind.LAPIS_ORE);
        }
    }

    private static final class AllStoneAccess implements LegacyOreBlockAccess {
        private final Map<Position, LegacyOreMaterial> ores = new HashMap<>();

        @Override
        public boolean isAccessible(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= -64 && y < 320;
        }

        @Override
        public LegacyBlockKind getBlockKind(int x, int y, int z) {
            LegacyOreMaterial material = ores.get(new Position(x, y, z));
            return material == null ? LegacyBlockKind.STONE : TrackingAccess.ORE_KINDS.get(material);
        }

        @Override
        public void setMaterial(int x, int y, int z, LegacyOreMaterial material) {
            ores.put(new Position(x, y, z), material);
        }

        Map<LegacyOreMaterial, Integer> counts() {
            Map<LegacyOreMaterial, Integer> counts = new EnumMap<>(LegacyOreMaterial.class);
            ores.values().forEach(material -> counts.merge(material, 1, Integer::sum));
            return Map.copyOf(counts);
        }

        long checksum() {
            long checksum = 0xcbf29ce484222325L;
            for (int y = 0; y < 68; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        LegacyOreMaterial material = ores.get(new Position(x, y, z));
                        int value = material == null ? -1 : material.ordinal();
                        checksum = (checksum ^ Integer.toUnsignedLong(value)) * 0x100000001b3L;
                    }
                }
            }
            return checksum;
        }
    }

    private record Position(int x, int y, int z) {
    }

    private record ConcurrentResult(
            LegacyOreApplicator.ApplicationSummary summary,
            Map<LegacyOreMaterial, Integer> counts,
            long checksum) {
    }
}
