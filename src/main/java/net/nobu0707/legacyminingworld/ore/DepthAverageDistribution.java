package net.nobu0707.legacyminingworld.ore;

import java.util.Objects;
import java.util.Random;

/** Two-call triangular origin-height distribution used by Java 1.16.5 lapis ore. */
public record DepthAverageDistribution(int baseline, int spread)
        implements LegacyOreHeightDistribution {
    public DepthAverageDistribution {
        if (spread <= 0) {
            throw new IllegalArgumentException("spread must be positive");
        }
        long minimum = (long) baseline - spread;
        long maximum = (long) baseline + spread - 2L;
        if (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("possible Y range must fit in an int");
        }
    }

    @Override
    public int sampleY(Random random) {
        Random checkedRandom = Objects.requireNonNull(random, "random");
        return baseline
                + checkedRandom.nextInt(spread)
                + checkedRandom.nextInt(spread)
                - spread;
    }

    @Override
    public boolean containsPossibleOriginY(int y) {
        return y >= minimumPossibleY() && y <= maximumPossibleY();
    }

    @Override
    public int minimumPossibleY() {
        return baseline - spread;
    }

    @Override
    public int maximumPossibleY() {
        return baseline + spread - 2;
    }
}
