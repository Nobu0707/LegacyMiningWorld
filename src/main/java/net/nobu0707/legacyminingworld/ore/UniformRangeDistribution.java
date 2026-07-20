package net.nobu0707.legacyminingworld.ore;

import java.util.Objects;
import java.util.Random;

/** One-call uniform origin-height distribution with an exclusive upper bound. */
public record UniformRangeDistribution(int minInclusive, int maxExclusive)
        implements LegacyOreHeightDistribution {
    public UniformRangeDistribution {
        if (maxExclusive <= minInclusive) {
            throw new IllegalArgumentException("maxExclusive must be greater than minInclusive");
        }
    }

    @Override
    public int sampleY(Random random) {
        return minInclusive
                + Objects.requireNonNull(random, "random").nextInt(maxExclusive - minInclusive);
    }

    @Override
    public boolean containsPossibleOriginY(int y) {
        return y >= minInclusive && y < maxExclusive;
    }

    @Override
    public int minimumPossibleY() {
        return minInclusive;
    }

    @Override
    public int maximumPossibleY() {
        return maxExclusive - 1;
    }
}
