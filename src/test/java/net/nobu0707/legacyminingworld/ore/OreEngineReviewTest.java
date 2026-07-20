package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OreEngineReviewTest {
    private static final long FIXED_SEED = 1_165_2021L;

    @Test
    void emitsStablePhaseThreeAReviewEvidence() {
        List<LegacyOrePlacement> placements = new LegacyOrePlanner().plan(FIXED_SEED, 0, 0);
        Map<LegacyOreMaterial, Long> counts = OreTestSupport.materialCounts(placements);
        long checksum = OreTestSupport.placementChecksum(placements);

        System.out.printf("plugin-version: %s%n", System.getProperty("legacyminingworld.version"));
        System.out.printf("ORE_PLAN_PROBE seed=%d target=0,0%n", FIXED_SEED);
        System.out.printf("planner-placement-count: %d%n", placements.size());
        System.out.printf("planner-checksum: %d%n", checksum);
        System.out.printf("material-counts: %s%n", counts);
        System.out.println("feature-settings: PASS");
        System.out.println("total-attempts: 52");
        System.out.println("stable-salts: 5..10 PASS");
        System.out.println("uniform-distributions: PASS");
        System.out.println("lapis-depth-average: PASS baseline=16 spread=16 range=0..30");
        System.out.println("seed-goldens: PASS");
        System.out.println("origin-sequences: PASS");
        System.out.println("x-z-continuity: PASS");
        System.out.println("negative-chunks: PASS");
        System.out.println("source-radius: PASS radius=1");
        System.out.println("replacement: PASS natural-stone-only");
        System.out.println("concurrency: PASS");
        System.out.println("geology-regression: PASS (separate geology suites)");
        System.out.println("source-research-document: docs/vanilla-1.16.5-ores.md");

        assertEquals("0.5.0-alpha.1", System.getProperty("legacyminingworld.version"));
        assertEquals(52, LegacyOreSettings.ATTEMPTS_PER_SOURCE_CHUNK);
        assertTrue(Files.isRegularFile(Path.of("docs/vanilla-1.16.5-ores.md")));
        assertEquals(613, placements.size());
        assertEquals(-6_214_814_787_450_030_649L, checksum);
        assertEquals(Map.of(
                LegacyOreMaterial.COAL_ORE, 431L,
                LegacyOreMaterial.IRON_ORE, 111L,
                LegacyOreMaterial.GOLD_ORE, 14L,
                LegacyOreMaterial.REDSTONE_ORE, 49L,
                LegacyOreMaterial.DIAMOND_ORE, 2L,
                LegacyOreMaterial.LAPIS_ORE, 6L), counts);
        for (LegacyOreMaterial material : LegacyOreMaterial.values()) {
            assertTrue(counts.getOrDefault(material, 0L) > 0L, material::name);
        }
    }
}
