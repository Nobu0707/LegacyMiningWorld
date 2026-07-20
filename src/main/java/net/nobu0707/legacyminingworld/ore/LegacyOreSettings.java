package net.nobu0707.legacyminingworld.ore;

import java.util.List;

/** Immutable settings for deterministic source-chunk ore reconstruction. */
public final class LegacyOreSettings {
    public static final int CHUNK_SIZE = 16;
    public static final int SOURCE_NEIGHBORHOOD_RADIUS = 1;
    public static final int ATTEMPTS_PER_SOURCE_CHUNK = 52;
    public static final List<LegacyOreFeature> FEATURES = List.of(
            LegacyOreFeature.COAL,
            LegacyOreFeature.IRON,
            LegacyOreFeature.GOLD,
            LegacyOreFeature.REDSTONE,
            LegacyOreFeature.DIAMOND,
            LegacyOreFeature.LAPIS);

    private LegacyOreSettings() {
    }
}
