package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class LiveComparableGoldenTest {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    @Test
    void freezesBedrockIndependentYFiveThroughSixtySevenGolden() {
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        Map<UndergroundWorldModelTest.ChunkPosition, Long> chunkChecksums =
                new LinkedHashMap<>();
        long combinedChecksum = FNV_OFFSET_BASIS;

        for (UndergroundWorldModelTest.ChunkPosition chunk : UndergroundWorldModelTest.CHUNKS) {
            InMemoryGeologyWorld world = new InMemoryGeologyWorld(
                    chunk.x(),
                    chunk.z(),
                    UndergroundWorldModelTest.MINIMUM_Y,
                    UndergroundWorldModelTest.MAXIMUM_Y_EXCLUSIVE);
            LegacyUndergroundPopulator.applyUnderground(
                    UndergroundWorldModelTest.FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    UndergroundWorldModelTest.MINIMUM_Y,
                    UndergroundWorldModelTest.MAXIMUM_Y_EXCLUSIVE,
                    world);
            long chunkChecksum = FNV_OFFSET_BASIS;
            int minimumX = chunk.x() << 4;
            int minimumZ = chunk.z() << 4;
            for (int y = 5; y <= 67; y++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        int x = minimumX + localX;
                        int z = minimumZ + localZ;
                        Material material = world.getMaterial(x, y, z);
                        totals.merge(material, 1, Integer::sum);
                        chunkChecksum = mixBlock(chunkChecksum, x, y, z, material);
                        combinedChecksum = mixBlock(combinedChecksum, x, y, z, material);
                    }
                }
            }
            chunkChecksums.put(chunk, chunkChecksum);
        }

        System.out.printf(
                "LIVE_COMPARABLE_GOLDEN seed=%d range=5..67 counts=%s chunkChecksums=%s checksum=%d%n",
                UndergroundWorldModelTest.FIXED_SEED,
                totals,
                chunkChecksums,
                combinedChecksum);
        assertEquals(Map.ofEntries(
                Map.entry(Material.STONE, 51_999),
                Map.entry(Material.DIRT, 995),
                Map.entry(Material.GRAVEL, 890),
                Map.entry(Material.GRANITE, 3_013),
                Map.entry(Material.DIORITE, 2_867),
                Map.entry(Material.ANDESITE, 3_279),
                Map.entry(Material.COAL_ORE, 860),
                Map.entry(Material.IRON_ORE, 438),
                Map.entry(Material.GOLD_ORE, 41),
                Map.entry(Material.REDSTONE_ORE, 94),
                Map.entry(Material.DIAMOND_ORE, 17),
                Map.entry(Material.LAPIS_ORE, 19)), totals);
        assertEquals(Map.of(
                new UndergroundWorldModelTest.ChunkPosition(-1, -1),
                        -4_081_461_885_369_063_153L,
                new UndergroundWorldModelTest.ChunkPosition(0, -1),
                        -6_459_175_142_289_166_354L,
                new UndergroundWorldModelTest.ChunkPosition(-1, 0),
                        4_995_189_412_391_713_686L,
                new UndergroundWorldModelTest.ChunkPosition(0, 0),
                        124_016_103_469_303_630L), chunkChecksums);
        assertEquals(-7_305_870_198_059_528_782L, combinedChecksum);
        assertEquals(63 * 256 * 4, totals.values().stream().mapToInt(Integer::intValue).sum());
    }

    private static long mixBlock(
            long checksum, int x, int y, int z, Material material) {
        long mixed = mix(checksum, x);
        mixed = mix(mixed, y);
        mixed = mix(mixed, z);
        for (char character : material.name().toCharArray()) {
            mixed = mix(mixed, character);
        }
        return mixed;
    }

    private static long mix(long checksum, int value) {
        return (checksum ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
    }
}
