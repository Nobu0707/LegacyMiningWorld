package net.nobu0707.legacyminingworld.ore;

import java.util.Random;

/** Pure Java Edition 1.16.5 origin-height strategy for an ore feature. */
public sealed interface LegacyOreHeightDistribution
        permits UniformRangeDistribution, DepthAverageDistribution {
    int sampleY(Random random);

    boolean containsPossibleOriginY(int y);

    int minimumPossibleY();

    int maximumPossibleY();
}
