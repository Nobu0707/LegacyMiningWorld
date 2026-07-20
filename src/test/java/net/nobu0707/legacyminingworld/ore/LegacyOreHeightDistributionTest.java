package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LegacyOreHeightDistributionTest {
    @Test
    void uniformFeaturesConsumeOneBoundedCallAndReachBothEndpoints() {
        assertUniform(LegacyOreFeature.COAL, 128);
        assertUniform(LegacyOreFeature.IRON, 64);
        assertUniform(LegacyOreFeature.GOLD, 32);
        assertUniform(LegacyOreFeature.REDSTONE, 16);
        assertUniform(LegacyOreFeature.DIAMOND, 16);
    }

    @Test
    void lapisConsumesExactlyTwoCallsAndCoversTheTriangularEndpoints() {
        DepthAverageDistribution distribution = new DepthAverageDistribution(16, 16);
        assertDepthAverageCase(distribution, 0, 0, 0);
        assertDepthAverageCase(distribution, 0, 15, 15);
        assertDepthAverageCase(distribution, 15, 0, 15);
        assertDepthAverageCase(distribution, 15, 15, 30);
        assertEquals(0, distribution.minimumPossibleY());
        assertEquals(30, distribution.maximumPossibleY());
        assertTrue(distribution.containsPossibleOriginY(0));
        assertTrue(distribution.containsPossibleOriginY(30));
        assertFalse(distribution.containsPossibleOriginY(-1));
        assertFalse(distribution.containsPossibleOriginY(31));
    }

    @Test
    void lapisPairFrequenciesAreExactlyTriangularWithoutClamping() {
        int[] frequencies = new int[31];
        DepthAverageDistribution distribution = new DepthAverageDistribution(16, 16);
        for (int first = 0; first < 16; first++) {
            for (int second = 0; second < 16; second++) {
                SequenceRandom random = new SequenceRandom(first, second);
                frequencies[distribution.sampleY(random)]++;
            }
        }
        assertEquals(1, frequencies[0]);
        assertEquals(16, frequencies[15]);
        assertEquals(1, frequencies[30]);
        assertTrue(frequencies[15] > frequencies[0]);
        assertTrue(frequencies[15] > frequencies[30]);
    }

    @Test
    void validatesDistributionConfigurationsAndNullRandoms() {
        assertThrows(IllegalArgumentException.class, () -> new UniformRangeDistribution(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new UniformRangeDistribution(5, 4));
        assertThrows(IllegalArgumentException.class, () -> new DepthAverageDistribution(16, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DepthAverageDistribution(Integer.MIN_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new DepthAverageDistribution(Integer.MAX_VALUE, 3));
        assertThrows(NullPointerException.class,
                () -> new UniformRangeDistribution(0, 16).sampleY(null));
        assertThrows(NullPointerException.class,
                () -> new DepthAverageDistribution(16, 16).sampleY(null));
    }

    private static void assertUniform(LegacyOreFeature feature, int bound) {
        UniformRangeDistribution distribution =
                (UniformRangeDistribution) feature.heightDistribution();
        SequenceRandom lower = new SequenceRandom(0);
        SequenceRandom upper = new SequenceRandom(bound - 1);
        assertEquals(0, distribution.sampleY(lower));
        assertEquals(bound - 1, distribution.sampleY(upper));
        assertEquals(List.of(bound), lower.bounds());
        assertEquals(List.of(bound), upper.bounds());
        assertTrue(distribution.containsPossibleOriginY(0));
        assertTrue(distribution.containsPossibleOriginY(bound - 1));
        assertFalse(distribution.containsPossibleOriginY(-1));
        assertFalse(distribution.containsPossibleOriginY(bound));
    }

    private static void assertDepthAverageCase(
            DepthAverageDistribution distribution, int first, int second, int expected) {
        SequenceRandom random = new SequenceRandom(first, second);
        assertEquals(expected, distribution.sampleY(random));
        assertEquals(List.of(16, 16), random.bounds());
    }

    private static final class SequenceRandom extends Random {
        private final Deque<Integer> values;
        private final List<Integer> bounds = new ArrayList<>();

        private SequenceRandom(int... values) {
            this.values = new ArrayDeque<>();
            for (int value : values) {
                this.values.add(value);
            }
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            int value = values.removeFirst();
            if (value < 0 || value >= bound) {
                throw new AssertionError("test value " + value + " is outside bound " + bound);
            }
            return value;
        }

        private List<Integer> bounds() {
            assertTrue(values.isEmpty(), "all configured test values must be consumed");
            return List.copyOf(bounds);
        }
    }
}
