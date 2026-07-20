package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;
import org.bukkit.generator.LimitedRegion;

/** Call-scoped adapter that reads and writes only through a supplied LimitedRegion. */
final class LimitedRegionGeologyBlockAccess implements LegacyGeologyBlockAccess {
    private final LimitedRegion limitedRegion;

    LimitedRegionGeologyBlockAccess(LimitedRegion limitedRegion) {
        this.limitedRegion = Objects.requireNonNull(limitedRegion, "limitedRegion");
    }

    @Override
    public boolean isAccessible(int x, int y, int z) {
        return limitedRegion.isInRegion(x, y, z);
    }

    @Override
    public LegacyBlockKind getBlockKind(int x, int y, int z) {
        return LegacyGeologyMaterialAdapter.toBlockKind(limitedRegion.getType(x, y, z));
    }

    @Override
    public void setMaterial(int x, int y, int z, LegacyGeologyMaterial material) {
        limitedRegion.setType(x, y, z, LegacyGeologyMaterialAdapter.toMaterial(material));
    }
}
