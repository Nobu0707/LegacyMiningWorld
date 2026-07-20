package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("geology-adapter")
class GeologyWorldModelTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y_EXCLUSIVE = 320;
    private static final List<ChunkPosition> CHUNKS = List.of(
            new ChunkPosition(-1, -1),
            new ChunkPosition(0, -1),
            new ChunkPosition(-1, 0),
            new ChunkPosition(0, 0));
    private final LegacyGeologyApplicator applicator = new LegacyGeologyApplicator();

    @Test
    void freezesFourChunkDistributionAndTargetOrderIndependence() {
        Map<ChunkPosition, ChunkResult> forward = applyInOrder(CHUNKS);
        Map<ChunkPosition, ChunkResult> reverse = applyInOrder(CHUNKS.reversed());
        Map<Material, Integer> totals = new EnumMap<>(Material.class);

        assertEquals(forward, reverse);
        for (ChunkPosition chunk : CHUNKS) {
            ChunkResult result = forward.get(chunk);
            System.out.printf(
                    "WORLD_MODEL_PROBE seed=%d chunk=%d,%d summary=%s counts=%s checksum=%d%n",
                    FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    result.summary(),
                    LegacyGeologyApplicatorTest.selectedCounts(result.counts()),
                    result.checksum());
            result.counts().forEach((material, count) -> totals.merge(material, count, Integer::sum));
            assertEquals(0, result.summary().skippedOutsideTargetChunk());
            assertEquals(0, result.summary().skippedOutOfRegion());
            assertEquals(256, result.counts().getOrDefault(Material.GRASS_BLOCK, 0));
            assertTrue(result.counts().getOrDefault(Material.STONE, 0) > 0);
            assertTrue(result.counts().getOrDefault(Material.BEDROCK, 0) > 0);
            assertChunkGolden(chunk, result);
        }
        for (Material material : List.of(
                Material.DIRT,
                Material.GRAVEL,
                Material.GRANITE,
                Material.DIORITE,
                Material.ANDESITE)) {
            assertTrue(totals.getOrDefault(material, 0) > 0, material::name);
        }
        long combinedChecksum = combinedChecksum(forward);
        System.out.printf(
                "WORLD_MODEL_TOTAL seed=%d chunks=-1,-1;0,-1;-1,0;0,0 counts=%s checksum=%d%n",
                FIXED_SEED,
                LegacyGeologyApplicatorTest.selectedCounts(totals),
                combinedChecksum);
        assertEquals(Map.of(
                Material.DIRT, 3_132,
                Material.GRAVEL, 960,
                Material.GRANITE, 3_187,
                Material.DIORITE, 3_080,
                Material.ANDESITE, 3_470,
                Material.STONE, 54_778,
                Material.BEDROCK, 3_073,
                Material.GRASS_BLOCK, 1_024,
                Material.AIR, 320_512), totals);
        assertEquals(-8_052_018_879_515_985_261L, combinedChecksum);
    }

    private Map<ChunkPosition, ChunkResult> applyInOrder(List<ChunkPosition> chunks) {
        Map<ChunkPosition, ChunkResult> results = new LinkedHashMap<>();
        for (ChunkPosition chunk : chunks) {
            InMemoryGeologyWorld world =
                    new InMemoryGeologyWorld(chunk.x(), chunk.z(), MINIMUM_Y, MAXIMUM_Y_EXCLUSIVE);
            var summary = applicator.apply(
                    FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    MINIMUM_Y,
                    MAXIMUM_Y_EXCLUSIVE,
                    world);
            assertProtectedTerrain(world, chunk);
            results.put(chunk, new ChunkResult(summary, world.materialCounts(), world.checksum()));
        }
        return results;
    }

    private static void assertProtectedTerrain(
            InMemoryGeologyWorld world, ChunkPosition chunk) {
        int minimumX = chunk.x() << 4;
        int minimumZ = chunk.z() << 4;
        for (int z = minimumZ; z < minimumZ + 16; z++) {
            for (int x = minimumX; x < minimumX + 16; x++) {
                assertEquals(Material.BEDROCK, world.getMaterial(x, 0, z));
                assertEquals(Material.DIRT, world.getMaterial(x, 68, z));
                assertEquals(Material.DIRT, world.getMaterial(x, 69, z));
                assertEquals(Material.GRASS_BLOCK, world.getMaterial(x, 70, z));
                assertEquals(Material.AIR, world.getMaterial(x, -1, z));
                assertEquals(Material.AIR, world.getMaterial(x, 71, z));
            }
        }
    }

    private static long combinedChecksum(Map<ChunkPosition, ChunkResult> results) {
        long checksum = 0xcbf29ce484222325L;
        for (ChunkPosition chunk : CHUNKS) {
            ChunkResult result = results.get(chunk);
            checksum = (checksum ^ Integer.toUnsignedLong(chunk.x())) * 0x100000001b3L;
            checksum = (checksum ^ Integer.toUnsignedLong(chunk.z())) * 0x100000001b3L;
            checksum = (checksum ^ result.checksum()) * 0x100000001b3L;
        }
        return checksum;
    }

    private static void assertChunkGolden(ChunkPosition chunk, ChunkResult actual) {
        ChunkResult expected = switch (chunk) {
            case ChunkPosition(int x, int z) when x == -1 && z == -1 -> new ChunkResult(
                    new LegacyGeologyApplicator.ApplicationSummary(5_142, 3_129, 0, 0, 0, 2_013),
                    counts(796, 411, 718, 807, 702, 13_718, 768),
                    7_636_051_648_872_243_184L);
            case ChunkPosition(int x, int z) when x == 0 && z == -1 -> new ChunkResult(
                    new LegacyGeologyApplicator.ApplicationSummary(5_543, 2_992, 0, 0, 0, 2_551),
                    counts(766, 207, 701, 650, 888, 13_939, 769),
                    -556_462_915_633_053_921L);
            case ChunkPosition(int x, int z) when x == -1 && z == 0 -> new ChunkResult(
                    new LegacyGeologyApplicator.ApplicationSummary(5_561, 3_255, 0, 0, 0, 2_306),
                    counts(687, 283, 908, 821, 897, 13_554, 770),
                    -3_889_388_701_578_752_134L);
            case ChunkPosition(int x, int z) when x == 0 && z == 0 -> new ChunkResult(
                    new LegacyGeologyApplicator.ApplicationSummary(5_564, 3_180, 0, 0, 0, 2_384),
                    counts(883, 59, 860, 802, 983, 13_567, 766),
                    8_312_497_771_964_804_421L);
            default -> throw new AssertionError("unexpected golden chunk: " + chunk);
        };
        assertEquals(expected, actual);
    }

    private static Map<Material, Integer> counts(
            int dirt,
            int gravel,
            int granite,
            int diorite,
            int andesite,
            int stone,
            int bedrock) {
        return Map.of(
                Material.DIRT, dirt,
                Material.GRAVEL, gravel,
                Material.GRANITE, granite,
                Material.DIORITE, diorite,
                Material.ANDESITE, andesite,
                Material.STONE, stone,
                Material.BEDROCK, bedrock,
                Material.GRASS_BLOCK, 256,
                Material.AIR, 80_128);
    }

    private record ChunkPosition(int x, int z) {
    }

    private record ChunkResult(
            LegacyGeologyApplicator.ApplicationSummary summary,
            Map<Material, Integer> counts,
            long checksum) {
    }
}
