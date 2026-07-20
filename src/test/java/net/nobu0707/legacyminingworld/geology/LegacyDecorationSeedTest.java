package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LegacyDecorationSeedTest {
    @Test
    void freezesDecorationAndFeatureSeedGoldenValues() {
        SeedCase[] cases = {
            new SeedCase(0L, 0, 0, 0L, 60_000L),
            new SeedCase(1L, 3, 7, 206_697_545_051_314_433L, 206_697_545_051_374_433L),
            new SeedCase(
                    -9_876_543_210L,
                    -4,
                    12,
                    -3_678_387_138_186_067_818L,
                    -3_678_387_138_186_007_818L),
            new SeedCase(
                    Long.MIN_VALUE,
                    -1,
                    0,
                    -3_606_052_880_870_871_952L,
                    -3_606_052_880_870_811_952L),
            new SeedCase(
                    Long.MAX_VALUE,
                    0,
                    -1,
                    -5_601_913_437_006_333_457L,
                    -5_601_913_437_006_273_457L),
            new SeedCase(
                    0x1234_5678_9abc_def0L,
                    1_000_000,
                    -1_000_000,
                    -6_641_482_270_171_066_640L,
                    -6_641_482_270_171_006_640L)
        };

        for (SeedCase seedCase : cases) {
            long decorationSeed = LegacyDecorationSeed.decorationSeed(
                    seedCase.worldSeed(), seedCase.chunkX(), seedCase.chunkZ());
            long featureSeed = LegacyDecorationSeed.featureSeed(
                    seedCase.worldSeed(),
                    seedCase.chunkX(),
                    seedCase.chunkZ(),
                    LegacyGeologyFeature.DIRT);
            assertEquals(seedCase.decorationSeed(), decorationSeed);
            assertEquals(seedCase.dirtFeatureSeed(), featureSeed);
        }
    }

    @Test
    void changesForTheSelectedWorldChunkAndFeatureCases() {
        long baseline = LegacyDecorationSeed.featureSeed(123L, 4, -5, LegacyGeologyFeature.DIRT);
        assertNotEquals(baseline,
                LegacyDecorationSeed.featureSeed(124L, 4, -5, LegacyGeologyFeature.DIRT));
        assertNotEquals(baseline,
                LegacyDecorationSeed.featureSeed(123L, 5, -5, LegacyGeologyFeature.DIRT));
        assertNotEquals(baseline,
                LegacyDecorationSeed.featureSeed(123L, 4, -4, LegacyGeologyFeature.DIRT));
        assertNotEquals(baseline,
                LegacyDecorationSeed.featureSeed(123L, 4, -5, LegacyGeologyFeature.GRAVEL));
        assertEquals(baseline,
                LegacyDecorationSeed.featureSeed(123L, 4, -5, LegacyGeologyFeature.DIRT));
    }

    @Test
    void derivesTheSameSeedsAcrossConcurrentCalls() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<Long> task = () -> LegacyDecorationSeed.featureSeed(
                    Long.MIN_VALUE, -123_456, 987_654, LegacyGeologyFeature.ANDESITE);
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                tasks.add(task);
            }
            long expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private record SeedCase(
            long worldSeed,
            int chunkX,
            int chunkZ,
            long decorationSeed,
            long dirtFeatureSeed) {
    }
}
