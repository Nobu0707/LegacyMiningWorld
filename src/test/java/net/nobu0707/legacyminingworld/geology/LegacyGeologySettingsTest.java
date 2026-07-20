package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class LegacyGeologySettingsTest {
    @Test
    void exposesOfficialOneSixteenFiveSettingsInStableOrder() {
        assertFeature(LegacyGeologyFeature.DIRT, LegacyGeologyMaterial.DIRT, 10, 0, 256, 0, 0);
        assertFeature(LegacyGeologyFeature.GRAVEL, LegacyGeologyMaterial.GRAVEL, 8, 0, 256, 1, 1);
        assertFeature(LegacyGeologyFeature.GRANITE, LegacyGeologyMaterial.GRANITE, 10, 0, 80, 2, 2);
        assertFeature(LegacyGeologyFeature.DIORITE, LegacyGeologyMaterial.DIORITE, 10, 0, 80, 3, 3);
        assertFeature(LegacyGeologyFeature.ANDESITE, LegacyGeologyMaterial.ANDESITE, 10, 0, 80, 4, 4);

        assertEquals(48, LegacyGeologySettings.FEATURES.stream()
                .mapToInt(LegacyGeologyFeature::attempts)
                .sum());
        assertEquals(48, LegacyGeologySettings.ATTEMPTS_PER_SOURCE_CHUNK);
        assertEquals(5, new HashSet<>(LegacyGeologySettings.FEATURES.stream()
                .map(LegacyGeologyFeature::stableSalt)
                .toList()).size());
        assertThrows(UnsupportedOperationException.class,
                () -> LegacyGeologySettings.FEATURES.add(LegacyGeologyFeature.DIRT));
    }

    private static void assertFeature(
            LegacyGeologyFeature feature,
            LegacyGeologyMaterial material,
            int attempts,
            int minimumY,
            int maximumYExclusive,
            int stableOrder,
            int stableSalt) {
        assertEquals(material, feature.material());
        assertEquals(33, feature.veinSize());
        assertEquals(attempts, feature.attempts());
        assertEquals(minimumY, feature.originMinY());
        assertEquals(maximumYExclusive, feature.originMaxYExclusive());
        assertEquals(stableOrder, feature.stableOrder());
        assertEquals(stableSalt, feature.stableSalt());
    }
}
