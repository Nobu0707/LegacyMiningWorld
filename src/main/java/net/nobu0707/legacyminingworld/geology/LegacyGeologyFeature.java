package net.nobu0707.legacyminingworld.geology;

/**
 * Stable Java Edition 1.16.5 underground-variety feature settings.
 *
 * <p>The stable salt is explicit rather than derived from {@link #ordinal()} so enum edits cannot
 * silently change the persisted seed contract.
 */
public enum LegacyGeologyFeature {
    DIRT(LegacyGeologyMaterial.DIRT, 33, 10, 0, 256, 0, 0),
    GRAVEL(LegacyGeologyMaterial.GRAVEL, 33, 8, 0, 256, 1, 1),
    GRANITE(LegacyGeologyMaterial.GRANITE, 33, 10, 0, 80, 2, 2),
    DIORITE(LegacyGeologyMaterial.DIORITE, 33, 10, 0, 80, 3, 3),
    ANDESITE(LegacyGeologyMaterial.ANDESITE, 33, 10, 0, 80, 4, 4);

    private final LegacyGeologyMaterial material;
    private final int veinSize;
    private final int attempts;
    private final int originMinY;
    private final int originMaxYExclusive;
    private final int stableOrder;
    private final int stableSalt;

    LegacyGeologyFeature(
            LegacyGeologyMaterial material,
            int veinSize,
            int attempts,
            int originMinY,
            int originMaxYExclusive,
            int stableOrder,
            int stableSalt) {
        this.material = material;
        this.veinSize = veinSize;
        this.attempts = attempts;
        this.originMinY = originMinY;
        this.originMaxYExclusive = originMaxYExclusive;
        this.stableOrder = stableOrder;
        this.stableSalt = stableSalt;
    }

    public LegacyGeologyMaterial material() {
        return material;
    }

    public int veinSize() {
        return veinSize;
    }

    public int attempts() {
        return attempts;
    }

    public int originMinY() {
        return originMinY;
    }

    public int originMaxYExclusive() {
        return originMaxYExclusive;
    }

    public int stableOrder() {
        return stableOrder;
    }

    public int stableSalt() {
        return stableSalt;
    }
}
