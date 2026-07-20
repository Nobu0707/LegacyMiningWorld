package net.nobu0707.legacyminingworld.ore;

/** Explicit Java Edition 1.16.5 overworld ore settings and persistent seed salts. */
public enum LegacyOreFeature {
    COAL(
            LegacyOreMaterial.COAL_ORE,
            17,
            20,
            new UniformRangeDistribution(0, 128),
            5,
            5),
    IRON(
            LegacyOreMaterial.IRON_ORE,
            9,
            20,
            new UniformRangeDistribution(0, 64),
            6,
            6),
    GOLD(
            LegacyOreMaterial.GOLD_ORE,
            9,
            2,
            new UniformRangeDistribution(0, 32),
            7,
            7),
    REDSTONE(
            LegacyOreMaterial.REDSTONE_ORE,
            8,
            8,
            new UniformRangeDistribution(0, 16),
            8,
            8),
    DIAMOND(
            LegacyOreMaterial.DIAMOND_ORE,
            8,
            1,
            new UniformRangeDistribution(0, 16),
            9,
            9),
    LAPIS(
            LegacyOreMaterial.LAPIS_ORE,
            7,
            1,
            new DepthAverageDistribution(16, 16),
            10,
            10);

    private final LegacyOreMaterial material;
    private final int veinSize;
    private final int attempts;
    private final LegacyOreHeightDistribution heightDistribution;
    private final int stableOrder;
    private final int stableSalt;

    LegacyOreFeature(
            LegacyOreMaterial material,
            int veinSize,
            int attempts,
            LegacyOreHeightDistribution heightDistribution,
            int stableOrder,
            int stableSalt) {
        this.material = material;
        this.veinSize = veinSize;
        this.attempts = attempts;
        this.heightDistribution = heightDistribution;
        this.stableOrder = stableOrder;
        this.stableSalt = stableSalt;
    }

    public LegacyOreMaterial material() {
        return material;
    }

    public int veinSize() {
        return veinSize;
    }

    public int attempts() {
        return attempts;
    }

    public LegacyOreHeightDistribution heightDistribution() {
        return heightDistribution;
    }

    public int stableOrder() {
        return stableOrder;
    }

    public int stableSalt() {
        return stableSalt;
    }
}
