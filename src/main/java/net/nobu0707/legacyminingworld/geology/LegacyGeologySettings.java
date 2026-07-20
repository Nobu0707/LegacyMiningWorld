package net.nobu0707.legacyminingworld.geology;

import java.util.List;

/** Immutable settings shared by planning and the Paper underground adapter. */
public final class LegacyGeologySettings {
    public static final int CHUNK_SIZE = 16;
    public static final int SOURCE_NEIGHBORHOOD_RADIUS = 1;
    public static final int ATTEMPTS_PER_SOURCE_CHUNK = 48;
    public static final List<LegacyGeologyFeature> FEATURES = List.of(
            LegacyGeologyFeature.DIRT,
            LegacyGeologyFeature.GRAVEL,
            LegacyGeologyFeature.GRANITE,
            LegacyGeologyFeature.DIORITE,
            LegacyGeologyFeature.ANDESITE);

    private LegacyGeologySettings() {
    }
}
