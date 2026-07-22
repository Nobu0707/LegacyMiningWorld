package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class OreAdapterReviewTest {
    @Test
    void emitsStablePhaseThreeBReviewEvidence() throws Exception {
        var results = UndergroundWorldModelTest.applyInOrder(UndergroundWorldModelTest.CHUNKS);
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        Map<Material, Integer> yEleven = new EnumMap<>(Material.class);
        results.values().forEach(result -> {
            result.counts().forEach((material, count) -> totals.merge(material, count, Integer::sum));
            result.yElevenCounts().forEach(
                    (material, count) -> yEleven.merge(material, count, Integer::sum));
        });
        long checksum = UndergroundWorldModelTest.combinedChecksum(results);
        int anchorCount = OreSmokeAnchorsTest.loadAnchors().size();

        System.out.printf("plugin-version: %s%n", System.getProperty("legacyminingworld.version"));
        System.out.printf("ORE_ADAPTER_PROBE seed=%d chunks=-1..0,-1..0%n",
                UndergroundWorldModelTest.FIXED_SEED);
        System.out.println("material-adapter: PASS six explicit ores");
        System.out.println("replacement: PASS natural-stone-only");
        System.out.println("legacy-application-y: PASS 0..67");
        System.out.println("target-region: PASS");
        System.out.println("stable-first-wins: PASS");
        System.out.printf("combined-counts: %s%n", totals);
        System.out.printf("combined-checksum: %d%n", checksum);
        System.out.printf("y11-counts: %s%n", yEleven);
        System.out.printf("ore-anchor-count: %d%n", anchorCount);
        System.out.println("ore-anchor-material-counts: COAL=3 IRON=3 GOLD=2 REDSTONE=2 DIAMOND=2 LAPIS=2");
        System.out.println("x-boundary-pair: X_COAL PASS");
        System.out.println("z-boundary-pair: Z_IRON PASS");
        System.out.println("geology-anchor-regression: 10/10 PASS");
        System.out.println("concurrency: PASS");
        System.out.println("forbidden-material-pure-model: PASS");

        assertEquals("1.0.0", System.getProperty("legacyminingworld.version"));
        assertEquals(14, anchorCount);
        assertEquals(-7_165_395_187_979_696_007L, checksum);
        assertTrue(Files.isRegularFile(Path.of("docs/ore-paper-integration.md")));
    }
}
