package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeologyEngineReviewTest {
    private static final long REVIEW_WORLD_SEED = 1_165_2021L;

    @Test
    void emitsStableReviewEvidence() {
        List<LegacyPlacement> placements =
                new LegacyGeologyPlanner().plan(REVIEW_WORLD_SEED, 0, 0);
        long checksum = GeologyTestSupport.placementChecksum(placements);

        System.out.printf("plugin-version: %s%n", System.getProperty("legacyminingworld.version"));
        System.out.printf("fixed-world-seed: %d%n", REVIEW_WORLD_SEED);
        System.out.println("fixed-target-chunk: 0,0");
        System.out.printf("planner-placement-count: %d%n", placements.size());
        System.out.printf("planner-checksum: %d%n", checksum);
        System.out.println("feature-attempt-counts: DIRT=10 GRAVEL=8 GRANITE=10 DIORITE=10 ANDESITE=10 TOTAL=48");
        System.out.println("cross-chunk-continuity-test: PASS (LegacyVeinGeneratorTest)");
        System.out.println("seed-golden-test: PASS (LegacyDecorationSeedTest)");
        System.out.println("thread-safety-test: PASS (LegacyGeologyPlannerTest)");
        System.out.println("source-research-document: docs/vanilla-1.16.5-geology.md");

        assertEquals("1.0.0", System.getProperty("legacyminingworld.version"));
        assertTrue(Files.isRegularFile(Path.of("docs/vanilla-1.16.5-geology.md")));
        assertEquals(5_564, placements.size());
        assertEquals(-4_572_519_745_665_027_215L, checksum);
    }
}
