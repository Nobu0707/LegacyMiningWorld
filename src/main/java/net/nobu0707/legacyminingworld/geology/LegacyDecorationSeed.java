package net.nobu0707.legacyminingworld.geology;

import java.util.Random;

/** Java Edition 1.16.5-style decoration and configured-feature seed derivation. */
public final class LegacyDecorationSeed {
    private static final int UNDERGROUND_ORES_DECORATION_STEP = 6;
    private static final long FEATURE_STEP_MULTIPLIER = 10_000L;

    private LegacyDecorationSeed() {
    }

    public static long decorationSeed(long worldSeed, int sourceChunkX, int sourceChunkZ) {
        Random random = new Random(worldSeed);
        long xMultiplier = random.nextLong() | 1L;
        long zMultiplier = random.nextLong() | 1L;
        int sourceBlockX = sourceChunkX << 4;
        int sourceBlockZ = sourceChunkZ << 4;
        return ((long) sourceBlockX * xMultiplier + (long) sourceBlockZ * zMultiplier) ^ worldSeed;
    }

    public static long featureSeed(
            long worldSeed, int sourceChunkX, int sourceChunkZ, LegacyGeologyFeature feature) {
        return featureSeed(
                worldSeed, sourceChunkX, sourceChunkZ, feature.stableSalt());
    }

    public static long featureSeed(long decorationSeed, LegacyGeologyFeature feature) {
        return featureSeed(decorationSeed, feature.stableSalt());
    }

    public static long featureSeed(
            long worldSeed, int sourceChunkX, int sourceChunkZ, int stableSalt) {
        long decorationSeed = decorationSeed(worldSeed, sourceChunkX, sourceChunkZ);
        return featureSeed(decorationSeed, stableSalt);
    }

    public static long featureSeed(long decorationSeed, int stableSalt) {
        return decorationSeed
                + stableSalt
                + FEATURE_STEP_MULTIPLIER * UNDERGROUND_ORES_DECORATION_STEP;
    }

    public static Random featureRandom(
            long worldSeed, int sourceChunkX, int sourceChunkZ, LegacyGeologyFeature feature) {
        return new Random(featureSeed(worldSeed, sourceChunkX, sourceChunkZ, feature));
    }

    public static Random featureRandom(
            long worldSeed, int sourceChunkX, int sourceChunkZ, int stableSalt) {
        return new Random(featureSeed(worldSeed, sourceChunkX, sourceChunkZ, stableSalt));
    }
}
