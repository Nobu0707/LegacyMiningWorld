package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.nobu0707.legacyminingworld.geology.LegacyDecorationSeed;
import net.nobu0707.legacyminingworld.geology.LegacyGeologyFeature;
import org.junit.jupiter.api.Test;

class LegacyOreSeedTest {
    @Test
    void derivesExplicitOreSaltsAfterTheUnchangedGeologySalts() {
        SeedInput[] inputs = {
            new SeedInput(0L, 0, 0, 0L, 60_005L, 60_010L),
            new SeedInput(1_165_2021L, 0, 0, 11_652_021L, 11_712_026L, 11_712_031L),
            new SeedInput(
                    -9_876_543_210L,
                    -4,
                    12,
                    -3_678_387_138_186_067_818L,
                    -3_678_387_138_186_007_813L,
                    -3_678_387_138_186_007_808L),
            new SeedInput(
                    Long.MIN_VALUE,
                    -1,
                    0,
                    -3_606_052_880_870_871_952L,
                    -3_606_052_880_870_811_947L,
                    -3_606_052_880_870_811_942L),
            new SeedInput(
                    Long.MAX_VALUE,
                    0,
                    -1,
                    -5_601_913_437_006_333_457L,
                    -5_601_913_437_006_273_452L,
                    -5_601_913_437_006_273_447L),
            new SeedInput(
                    0x1234_5678_9abc_def0L,
                    1_000_000,
                    -1_000_000,
                    -6_641_482_270_171_066_640L,
                    -6_641_482_270_171_006_635L,
                    -6_641_482_270_171_006_630L)
        };
        for (SeedInput input : inputs) {
            long decorationSeed = LegacyDecorationSeed.decorationSeed(
                    input.worldSeed(), input.sourceChunkX(), input.sourceChunkZ());
            long coalSeed = LegacyDecorationSeed.featureSeed(decorationSeed, 5);
            long lapisSeed = LegacyDecorationSeed.featureSeed(decorationSeed, 10);
            System.out.printf(
                    "ORE_SEED_PROBE worldSeed=%d source=%d,%d decoration=%d coal=%d lapis=%d%n",
                    input.worldSeed(),
                    input.sourceChunkX(),
                    input.sourceChunkZ(),
                    decorationSeed,
                    coalSeed,
                    lapisSeed);
            assertEquals(input.decorationSeed(), decorationSeed);
            assertEquals(input.coalSeed(), coalSeed);
            assertEquals(input.lapisSeed(), lapisSeed);
            assertEquals(decorationSeed + 60_005L, coalSeed);
            assertEquals(decorationSeed + 60_010L, lapisSeed);
            assertNotEquals(coalSeed, lapisSeed);
            for (int salt = 0; salt <= 10; salt++) {
                assertEquals(
                        decorationSeed + 60_000L + salt,
                        LegacyDecorationSeed.featureSeed(decorationSeed, salt));
                assertEquals(
                        LegacyDecorationSeed.featureSeed(
                                input.worldSeed(), input.sourceChunkX(), input.sourceChunkZ(), salt),
                        LegacyDecorationSeed.featureSeed(decorationSeed, salt));
            }
        }

        assertEquals(60_000L,
                LegacyDecorationSeed.featureSeed(0L, 0, 0, LegacyGeologyFeature.DIRT));
        assertEquals(60_004L,
                LegacyDecorationSeed.featureSeed(0L, 0, 0, LegacyGeologyFeature.ANDESITE));
    }

    @Test
    void changesWithWorldChunkAndFeatureButRepeatsForIdenticalInputs() {
        long baseline = LegacyDecorationSeed.featureSeed(123L, 4, -5, 5);
        assertNotEquals(baseline, LegacyDecorationSeed.featureSeed(124L, 4, -5, 5));
        assertNotEquals(baseline, LegacyDecorationSeed.featureSeed(123L, 5, -5, 5));
        assertNotEquals(baseline, LegacyDecorationSeed.featureSeed(123L, 4, -4, 5));
        assertNotEquals(baseline, LegacyDecorationSeed.featureSeed(123L, 4, -5, 6));
        assertEquals(baseline, LegacyDecorationSeed.featureSeed(123L, 4, -5, 5));
    }

    @Test
    void derivesOreSeedsConcurrentlyWithoutSharedRandomState() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<Long> task = () -> LegacyDecorationSeed.featureSeed(
                    Long.MIN_VALUE, -123_456, 987_654, LegacyOreFeature.LAPIS.stableSalt());
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

    private record SeedInput(
            long worldSeed,
            int sourceChunkX,
            int sourceChunkZ,
            long decorationSeed,
            long coalSeed,
            long lapisSeed) {
    }
}
