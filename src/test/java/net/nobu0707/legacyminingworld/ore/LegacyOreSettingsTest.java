package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import net.nobu0707.legacyminingworld.geology.LegacyGeologyFeature;
import org.junit.jupiter.api.Test;

class LegacyOreSettingsTest {
    @Test
    void exposesOfficialOneSixteenFiveOreSettingsInStableOrder() {
        assertUniformFeature(
                LegacyOreFeature.COAL, LegacyOreMaterial.COAL_ORE, 17, 20, 0, 128, 5);
        assertUniformFeature(
                LegacyOreFeature.IRON, LegacyOreMaterial.IRON_ORE, 9, 20, 0, 64, 6);
        assertUniformFeature(
                LegacyOreFeature.GOLD, LegacyOreMaterial.GOLD_ORE, 9, 2, 0, 32, 7);
        assertUniformFeature(
                LegacyOreFeature.REDSTONE, LegacyOreMaterial.REDSTONE_ORE, 8, 8, 0, 16, 8);
        assertUniformFeature(
                LegacyOreFeature.DIAMOND, LegacyOreMaterial.DIAMOND_ORE, 8, 1, 0, 16, 9);

        LegacyOreFeature lapis = LegacyOreFeature.LAPIS;
        assertEquals(LegacyOreMaterial.LAPIS_ORE, lapis.material());
        assertEquals(7, lapis.veinSize());
        assertEquals(1, lapis.attempts());
        DepthAverageDistribution distribution = assertInstanceOf(
                DepthAverageDistribution.class, lapis.heightDistribution());
        assertEquals(16, distribution.baseline());
        assertEquals(16, distribution.spread());
        assertEquals(10, lapis.stableOrder());
        assertEquals(10, lapis.stableSalt());

        assertEquals(52, LegacyOreSettings.FEATURES.stream()
                .mapToInt(LegacyOreFeature::attempts)
                .sum());
        assertEquals(52, LegacyOreSettings.ATTEMPTS_PER_SOURCE_CHUNK);
        assertEquals(0, LegacyOreSettings.MINIMUM_APPLICATION_Y);
        assertEquals(68, LegacyOreSettings.MAXIMUM_APPLICATION_Y_EXCLUSIVE);
        assertEquals(Set.of(5, 6, 7, 8, 9, 10), new HashSet<>(LegacyOreSettings.FEATURES.stream()
                .map(LegacyOreFeature::stableSalt)
                .toList()));
        Set<Integer> geologySalts = new HashSet<>();
        for (LegacyGeologyFeature feature : LegacyGeologyFeature.values()) {
            geologySalts.add(feature.stableSalt());
        }
        Set<Integer> oreSalts = new HashSet<>(LegacyOreSettings.FEATURES.stream()
                .map(LegacyOreFeature::stableSalt)
                .toList());
        assertEquals(Set.of(), intersection(geologySalts, oreSalts));
        assertThrows(UnsupportedOperationException.class,
                () -> LegacyOreSettings.FEATURES.add(LegacyOreFeature.COAL));
    }

    private static void assertUniformFeature(
            LegacyOreFeature feature,
            LegacyOreMaterial material,
            int size,
            int attempts,
            int minimumY,
            int maximumYExclusive,
            int stableValue) {
        assertEquals(material, feature.material());
        assertEquals(size, feature.veinSize());
        assertEquals(attempts, feature.attempts());
        UniformRangeDistribution distribution = assertInstanceOf(
                UniformRangeDistribution.class, feature.heightDistribution());
        assertEquals(minimumY, distribution.minInclusive());
        assertEquals(maximumYExclusive, distribution.maxExclusive());
        assertEquals(stableValue, feature.stableOrder());
        assertEquals(stableValue, feature.stableSalt());
    }

    private static Set<Integer> intersection(Set<Integer> first, Set<Integer> second) {
        Set<Integer> result = new HashSet<>(first);
        result.retainAll(second);
        return Set.copyOf(result);
    }
}
