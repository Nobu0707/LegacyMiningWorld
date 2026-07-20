package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterial;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterialAdapter;
import org.bukkit.generator.LimitedRegion;

/** Call-scoped adapter shared by geology and ore writes within one population callback. */
final class LimitedRegionUndergroundBlockAccess implements LegacyUndergroundBlockAccess {
    private final LimitedRegion limitedRegion;

    LimitedRegionUndergroundBlockAccess(LimitedRegion limitedRegion) {
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

    @Override
    public void setMaterial(int x, int y, int z, LegacyOreMaterial material) {
        limitedRegion.setType(x, y, z, LegacyOreMaterialAdapter.toMaterial(material));
    }
}
