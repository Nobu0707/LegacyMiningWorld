package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class UndergroundWorldModelTest {
    static final long FIXED_SEED = 1_165_2021L;
    static final int MINIMUM_Y = -64;
    static final int MAXIMUM_Y_EXCLUSIVE = 320;
    static final List<ChunkPosition> CHUNKS = List.of(
            new ChunkPosition(-1, -1),
            new ChunkPosition(0, -1),
            new ChunkPosition(-1, 0),
            new ChunkPosition(0, 0));

    @Test
    void freezesCombinedFourChunkModelYElevenAndProtection() {
        Map<ChunkPosition, ChunkResult> forward = applyInOrder(CHUNKS);
        Map<ChunkPosition, ChunkResult> reverse = applyInOrder(CHUNKS.reversed());
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        Map<Material, Integer> yElevenTotals = new EnumMap<>(Material.class);

        assertEquals(forward, reverse);
        for (ChunkPosition chunk : CHUNKS) {
            ChunkResult result = forward.get(chunk);
            result.counts().forEach((material, count) -> totals.merge(material, count, Integer::sum));
            result.yElevenCounts().forEach(
                    (material, count) -> yElevenTotals.merge(material, count, Integer::sum));
            System.out.printf(
                    "UNDERGROUND_MODEL_PROBE seed=%d chunk=%d,%d geology=%s ore=%s counts=%s y11=%s checksum=%d%n",
                    FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    result.summary().geology(),
                    result.summary().ore(),
                    result.counts(),
                    result.yElevenCounts(),
                    result.checksum());
            assertEquals(0, result.summary().geology().skippedOutsideTargetChunk());
            assertEquals(0, result.summary().geology().skippedOutOfRegion());
            assertEquals(0, result.summary().ore().skippedOutsideTargetChunk());
            assertEquals(0, result.summary().ore().skippedOutOfRegion());
            assertEquals(256, result.counts().getOrDefault(Material.GRASS_BLOCK, 0));
        }

        for (Material material : List.of(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE,
                Material.LAPIS_ORE,
                Material.STONE,
                Material.DIRT,
                Material.GRAVEL,
                Material.GRANITE,
                Material.DIORITE,
                Material.ANDESITE,
                Material.BEDROCK)) {
            assertTrue(totals.getOrDefault(material, 0) > 0, material::name);
        }
        for (Material material : List.of(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE)) {
            assertTrue(yElevenTotals.getOrDefault(material, 0) > 0, material::name);
        }
        for (Material forbidden : List.of(
                Material.COPPER_ORE,
                Material.EMERALD_ORE,
                Material.DEEPSLATE,
                Material.DEEPSLATE_COAL_ORE,
                Material.DEEPSLATE_IRON_ORE,
                Material.DEEPSLATE_GOLD_ORE,
                Material.DEEPSLATE_REDSTONE_ORE,
                Material.DEEPSLATE_DIAMOND_ORE,
                Material.DEEPSLATE_LAPIS_ORE,
                Material.DEEPSLATE_EMERALD_ORE,
                Material.DEEPSLATE_COPPER_ORE,
                Material.TUFF,
                Material.CALCITE,
                Material.WATER,
                Material.LAVA)) {
            assertEquals(0, totals.getOrDefault(forbidden, 0), forbidden::name);
        }

        long combinedChecksum = combinedChecksum(forward);
        System.out.printf(
                "UNDERGROUND_MODEL_TOTAL seed=%d counts=%s checksum=%d%n",
                FIXED_SEED, totals, combinedChecksum);
        System.out.printf("UNDERGROUND_Y11_TOTAL seed=%d counts=%s%n", FIXED_SEED, yElevenTotals);
        assertEquals(Map.ofEntries(
                Map.entry(Material.AIR, 320_512),
                Map.entry(Material.BEDROCK, 3_073),
                Map.entry(Material.STONE, 53_542),
                Map.entry(Material.DIRT, 3_132),
                Map.entry(Material.GRAVEL, 960),
                Map.entry(Material.GRANITE, 3_098),
                Map.entry(Material.DIORITE, 3_017),
                Map.entry(Material.ANDESITE, 3_358),
                Map.entry(Material.GRASS_BLOCK, 1_024),
                Map.entry(Material.COAL_ORE, 867),
                Map.entry(Material.IRON_ORE, 443),
                Map.entry(Material.GOLD_ORE, 48),
                Map.entry(Material.REDSTONE_ORE, 106),
                Map.entry(Material.DIAMOND_ORE, 17),
                Map.entry(Material.LAPIS_ORE, 19)), totals);
        assertEquals(Map.ofEntries(
                Map.entry(Material.STONE, 733),
                Map.entry(Material.DIRT, 47),
                Map.entry(Material.GRAVEL, 19),
                Map.entry(Material.GRANITE, 76),
                Map.entry(Material.DIORITE, 34),
                Map.entry(Material.ANDESITE, 85),
                Map.entry(Material.COAL_ORE, 6),
                Map.entry(Material.IRON_ORE, 5),
                Map.entry(Material.GOLD_ORE, 8),
                Map.entry(Material.REDSTONE_ORE, 7),
                Map.entry(Material.DIAMOND_ORE, 4)), yElevenTotals);
        assertEquals(-7_165_395_187_979_696_007L, combinedChecksum);
        assertChunkGolden(new ChunkPosition(-1, -1), forward.get(new ChunkPosition(-1, -1)),
                362, 8_200_217_911_935_443_408L);
        assertChunkGolden(new ChunkPosition(0, -1), forward.get(new ChunkPosition(0, -1)),
                417, 834_795_681_032_079_842L);
        assertChunkGolden(new ChunkPosition(-1, 0), forward.get(new ChunkPosition(-1, 0)),
                347, -5_789_077_494_783_116_012L);
        assertChunkGolden(new ChunkPosition(0, 0), forward.get(new ChunkPosition(0, 0)),
                374, -7_748_694_558_319_417_456L);
    }

    static Map<ChunkPosition, ChunkResult> applyInOrder(List<ChunkPosition> chunks) {
        Map<ChunkPosition, ChunkResult> results = new LinkedHashMap<>();
        for (ChunkPosition chunk : chunks) {
            InMemoryGeologyWorld world = new InMemoryGeologyWorld(
                    chunk.x(), chunk.z(), MINIMUM_Y, MAXIMUM_Y_EXCLUSIVE);
            var summary = LegacyUndergroundPopulator.applyUnderground(
                    FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    MINIMUM_Y,
                    MAXIMUM_Y_EXCLUSIVE,
                    world);
            assertProtectedTerrain(world, chunk);
            results.put(chunk, new ChunkResult(
                    summary, world.materialCounts(), countsAtY(world, chunk, 11), world.checksum()));
        }
        return results;
    }

    private static void assertProtectedTerrain(InMemoryGeologyWorld world, ChunkPosition chunk) {
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
                for (int y = MINIMUM_Y; y < 0; y++) {
                    assertEquals(Material.AIR, world.getMaterial(x, y, z));
                }
                for (int y = 71; y < MAXIMUM_Y_EXCLUSIVE; y++) {
                    assertEquals(Material.AIR, world.getMaterial(x, y, z));
                }
            }
        }
    }

    private static Map<Material, Integer> countsAtY(
            InMemoryGeologyWorld world, ChunkPosition chunk, int y) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        int minimumX = chunk.x() << 4;
        int minimumZ = chunk.z() << 4;
        for (int z = minimumZ; z < minimumZ + 16; z++) {
            for (int x = minimumX; x < minimumX + 16; x++) {
                counts.merge(world.getMaterial(x, y, z), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    static long combinedChecksum(Map<ChunkPosition, ChunkResult> results) {
        long checksum = 0xcbf29ce484222325L;
        for (ChunkPosition chunk : CHUNKS) {
            ChunkResult result = results.get(chunk);
            checksum = (checksum ^ Integer.toUnsignedLong(chunk.x())) * 0x100000001b3L;
            checksum = (checksum ^ Integer.toUnsignedLong(chunk.z())) * 0x100000001b3L;
            checksum = (checksum ^ result.checksum()) * 0x100000001b3L;
        }
        return checksum;
    }

    private static void assertChunkGolden(
            ChunkPosition chunk, ChunkResult result, int appliedOres, long checksum) {
        assertEquals(appliedOres, result.summary().ore().applied(), chunk::toString);
        assertEquals(expectedOreSummary(chunk), result.summary().ore(), chunk::toString);
        assertEquals(expectedCounts(chunk), result.counts(), chunk::toString);
        assertEquals(checksum, result.checksum(), chunk::toString);
    }

    private static net.nobu0707.legacyminingworld.ore.LegacyOreApplicator.ApplicationSummary
            expectedOreSummary(ChunkPosition chunk) {
        if (chunk.equals(new ChunkPosition(-1, -1))) {
            return new net.nobu0707.legacyminingworld.ore.LegacyOreApplicator.ApplicationSummary(
                    604, 362, 0, 201, 0, 0, 41);
        }
        if (chunk.equals(new ChunkPosition(0, -1))) {
            return new net.nobu0707.legacyminingworld.ore.LegacyOreApplicator.ApplicationSummary(
                    626, 417, 0, 178, 0, 0, 31);
        }
        if (chunk.equals(new ChunkPosition(-1, 0))) {
            return new net.nobu0707.legacyminingworld.ore.LegacyOreApplicator.ApplicationSummary(
                    607, 347, 0, 215, 0, 0, 45);
        }
        if (chunk.equals(new ChunkPosition(0, 0))) {
            return new net.nobu0707.legacyminingworld.ore.LegacyOreApplicator.ApplicationSummary(
                    613, 374, 0, 203, 0, 0, 36);
        }
        throw new AssertionError("unexpected chunk: " + chunk);
    }

    private static Map<Material, Integer> expectedCounts(ChunkPosition chunk) {
        if (chunk.equals(new ChunkPosition(-1, -1))) {
            return counts(80_128, 768, 13_412, 796, 411, 707, 785, 679,
                    213, 107, 10, 25, 3, 4);
        }
        if (chunk.equals(new ChunkPosition(0, -1))) {
            return counts(80_128, 769, 13_592, 766, 207, 665, 634, 870,
                    246, 127, 11, 20, 8, 5);
        }
        if (chunk.equals(new ChunkPosition(-1, 0))) {
            return counts(80_128, 770, 13_258, 687, 283, 890, 808, 877,
                    197, 106, 15, 21, 4, 4);
        }
        if (chunk.equals(new ChunkPosition(0, 0))) {
            return counts(80_128, 766, 13_280, 883, 59, 836, 790, 932,
                    211, 103, 12, 40, 2, 6);
        }
        throw new AssertionError("unexpected chunk: " + chunk);
    }

    private static Map<Material, Integer> counts(
            int air,
            int bedrock,
            int stone,
            int dirt,
            int gravel,
            int granite,
            int diorite,
            int andesite,
            int coal,
            int iron,
            int gold,
            int redstone,
            int diamond,
            int lapis) {
        return Map.ofEntries(
                Map.entry(Material.AIR, air),
                Map.entry(Material.BEDROCK, bedrock),
                Map.entry(Material.STONE, stone),
                Map.entry(Material.DIRT, dirt),
                Map.entry(Material.GRAVEL, gravel),
                Map.entry(Material.GRANITE, granite),
                Map.entry(Material.DIORITE, diorite),
                Map.entry(Material.ANDESITE, andesite),
                Map.entry(Material.GRASS_BLOCK, 256),
                Map.entry(Material.COAL_ORE, coal),
                Map.entry(Material.IRON_ORE, iron),
                Map.entry(Material.GOLD_ORE, gold),
                Map.entry(Material.REDSTONE_ORE, redstone),
                Map.entry(Material.DIAMOND_ORE, diamond),
                Map.entry(Material.LAPIS_ORE, lapis));
    }

    record ChunkPosition(int x, int z) {
    }

    record ChunkResult(
            LegacyUndergroundPopulator.PopulationSummary summary,
            Map<Material, Integer> counts,
            Map<Material, Integer> yElevenCounts,
            long checksum) {
    }
}
