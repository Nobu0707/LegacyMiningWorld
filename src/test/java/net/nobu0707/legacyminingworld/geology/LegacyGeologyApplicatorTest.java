package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("geology-adapter")
class LegacyGeologyApplicatorTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y_EXCLUSIVE = 320;
    private final LegacyGeologyApplicator applicator = new LegacyGeologyApplicator();

    @Test
    void appliesTheFixedChunkDeterministicallyWithoutDamagingProtectedTerrain() {
        AppliedChunk first = applyChunk(0, 0);
        AppliedChunk second = applyChunk(0, 0);

        System.out.printf(
                "ADAPTER_PROBE seed=%d target=0,0 summary=%s counts=%s checksum=%d%n",
                FIXED_SEED, first.summary(), selectedCounts(first.counts()), first.checksum());

        assertEquals(first.summary(), second.summary());
        assertEquals(first.counts(), second.counts());
        assertEquals(first.checksum(), second.checksum());
        assertEquals(5_564, first.summary().planned());
        assertTrue(first.summary().applied() > 0);
        assertEquals(0, first.summary().skippedByHeight());
        assertEquals(0, first.summary().skippedOutsideTargetChunk());
        assertEquals(0, first.summary().skippedOutOfRegion());
        assertTrue(first.summary().skippedNotReplaceable() > 0);
        for (Material generated : List.of(
                Material.DIRT,
                Material.GRAVEL,
                Material.GRANITE,
                Material.DIORITE,
                Material.ANDESITE)) {
            assertTrue(first.counts().getOrDefault(generated, 0) > 0, generated::name);
        }
        assertTrue(first.counts().getOrDefault(Material.STONE, 0) > 0);
        assertTrue(first.counts().getOrDefault(Material.BEDROCK, 0) > 0);
        assertEquals(256, first.counts().getOrDefault(Material.GRASS_BLOCK, 0));
        assertEquals(512, first.counts().getOrDefault(Material.DIRT, 0)
                - generatedDirtBelowSurface(first));
        assertNotEquals(Material.AIR, first.world().getMaterial(0, 5, 0));
        assertEquals(Material.BEDROCK, first.world().getMaterial(0, 0, 0));
        assertEquals(Material.DIRT, first.world().getMaterial(0, 68, 0));
        assertEquals(Material.DIRT, first.world().getMaterial(0, 69, 0));
        assertEquals(Material.GRASS_BLOCK, first.world().getMaterial(0, 70, 0));
        assertEquals(Material.AIR, first.world().getMaterial(0, -1, 0));
        assertEquals(Material.AIR, first.world().getMaterial(0, 71, 0));

        assertEquals(3_180, first.summary().applied());
        assertEquals(2_384, first.summary().skippedNotReplaceable());
        assertEquals(883, first.counts().getOrDefault(Material.DIRT, 0));
        assertEquals(59, first.counts().getOrDefault(Material.GRAVEL, 0));
        assertEquals(860, first.counts().getOrDefault(Material.GRANITE, 0));
        assertEquals(802, first.counts().getOrDefault(Material.DIORITE, 0));
        assertEquals(983, first.counts().getOrDefault(Material.ANDESITE, 0));
        assertEquals(13_567, first.counts().getOrDefault(Material.STONE, 0));
        assertEquals(766, first.counts().getOrDefault(Material.BEDROCK, 0));
        assertEquals(8_312_497_771_964_804_421L, first.checksum());
    }

    @Test
    void preservesPlannerOrderAndNaturalStoneReplacementRules() {
        InMemoryGeologyWorld world = new InMemoryGeologyWorld(0, 0, 0, 32);
        world.setInitialMaterial(1, 10, 1, Material.STONE);
        world.setInitialMaterial(2, 10, 1, Material.GRAVEL);
        world.setInitialMaterial(3, 10, 1, Material.BEDROCK);
        List<LegacyPlacement> placements = List.of(
                placement(1, LegacyGeologyMaterial.GRANITE),
                placement(1, LegacyGeologyMaterial.DIORITE),
                placement(1, LegacyGeologyMaterial.ANDESITE),
                placement(1, LegacyGeologyMaterial.DIRT),
                placement(1, LegacyGeologyMaterial.GRAVEL),
                placement(2, LegacyGeologyMaterial.GRANITE),
                placement(3, LegacyGeologyMaterial.DIRT),
                placement(3, LegacyGeologyMaterial.ANDESITE));

        var summary = applicator.applyPlacements(placements, 0, 0, 0, 32, world);

        assertEquals(Material.DIRT, world.getMaterial(1, 10, 1));
        assertEquals(Material.GRAVEL, world.getMaterial(2, 10, 1));
        assertEquals(Material.BEDROCK, world.getMaterial(3, 10, 1));
        assertEquals(8, summary.planned());
        assertEquals(4, summary.applied());
        assertEquals(4, summary.skippedNotReplaceable());
    }

    @Test
    void checksTargetHeightAndRegionBeforeReadingOrWritingIncludingNegativeChunks() {
        InMemoryGeologyWorld world = new InMemoryGeologyWorld(-1, -1, -64, 320);
        world.setInitialMaterial(-16, -64, -16, Material.STONE);
        world.setInitialMaterial(-16, 319, -16, Material.STONE);
        world.deny(-15, 5, -16);
        List<LegacyPlacement> placements = List.of(
                placement(-16, -65, -16, LegacyGeologyMaterial.GRANITE),
                placement(-16, -64, -16, LegacyGeologyMaterial.GRANITE),
                placement(-16, 319, -16, LegacyGeologyMaterial.GRANITE),
                placement(-16, 320, -16, LegacyGeologyMaterial.GRANITE),
                placement(-15, 5, -16, LegacyGeologyMaterial.GRANITE),
                placement(0, 5, -16, LegacyGeologyMaterial.GRANITE),
                placement(-16, 5, 0, LegacyGeologyMaterial.GRANITE));

        var summary = applicator.applyPlacements(placements, -1, -1, -64, 320, world);

        assertEquals(7, summary.planned());
        assertEquals(2, summary.applied());
        assertEquals(2, summary.skippedByHeight());
        assertEquals(2, summary.skippedOutsideTargetChunk());
        assertEquals(1, summary.skippedOutOfRegion());
        assertEquals(0, summary.skippedNotReplaceable());
        assertEquals(2, world.getCalls());
        assertEquals(2, world.setCalls());
        assertEquals(0, world.inaccessibleGetOrSetCalls());
    }

    @Test
    void reusesTheApplicatorConcurrentlyAgainstIndependentRegions() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<AppliedResult> task = () -> {
                AppliedChunk chunk = applyChunk(-1, 0);
                return new AppliedResult(chunk.summary(), chunk.checksum(), chunk.counts());
            };
            List<Callable<AppliedResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(task);
            }
            AppliedResult expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private AppliedChunk applyChunk(int chunkX, int chunkZ) {
        InMemoryGeologyWorld world =
                new InMemoryGeologyWorld(chunkX, chunkZ, MINIMUM_Y, MAXIMUM_Y_EXCLUSIVE);
        var summary = applicator.apply(
                FIXED_SEED,
                chunkX,
                chunkZ,
                MINIMUM_Y,
                MAXIMUM_Y_EXCLUSIVE,
                world);
        return new AppliedChunk(summary, world.materialCounts(), world.checksum(), world);
    }

    private static int generatedDirtBelowSurface(AppliedChunk chunk) {
        int count = 0;
        int minimumX = 0;
        int minimumZ = 0;
        for (int y = 0; y <= 67; y++) {
            for (int z = minimumZ; z < minimumZ + 16; z++) {
                for (int x = minimumX; x < minimumX + 16; x++) {
                    if (chunk.world().getMaterial(x, y, z) == Material.DIRT) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    static Map<Material, Integer> selectedCounts(Map<Material, Integer> counts) {
        return Map.of(
                Material.DIRT, counts.getOrDefault(Material.DIRT, 0),
                Material.GRAVEL, counts.getOrDefault(Material.GRAVEL, 0),
                Material.GRANITE, counts.getOrDefault(Material.GRANITE, 0),
                Material.DIORITE, counts.getOrDefault(Material.DIORITE, 0),
                Material.ANDESITE, counts.getOrDefault(Material.ANDESITE, 0),
                Material.STONE, counts.getOrDefault(Material.STONE, 0),
                Material.BEDROCK, counts.getOrDefault(Material.BEDROCK, 0),
                Material.GRASS_BLOCK, counts.getOrDefault(Material.GRASS_BLOCK, 0),
                Material.AIR, counts.getOrDefault(Material.AIR, 0));
    }

    private static LegacyPlacement placement(int x, LegacyGeologyMaterial material) {
        return placement(x, 10, 1, material);
    }

    private static LegacyPlacement placement(
            int x, int y, int z, LegacyGeologyMaterial material) {
        return new LegacyPlacement(x, y, z, material, 0, 0, 0, 0, 0);
    }

    private record AppliedChunk(
            LegacyGeologyApplicator.ApplicationSummary summary,
            Map<Material, Integer> counts,
            long checksum,
            InMemoryGeologyWorld world) {
    }

    private record AppliedResult(
            LegacyGeologyApplicator.ApplicationSummary summary,
            long checksum,
            Map<Material, Integer> counts) {
    }
}
