package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;
import java.util.Random;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/** Stateless Paper entry point for the deterministic legacy geology planner. */
public final class LegacyGeologyPopulator extends BlockPopulator {
    private static final LegacyGeologyApplicator APPLICATOR = new LegacyGeologyApplicator();

    @Override
    public void populate(
            WorldInfo worldInfo,
            Random random,
            int chunkX,
            int chunkZ,
            LimitedRegion limitedRegion) {
        Objects.requireNonNull(worldInfo, "worldInfo");
        Objects.requireNonNull(limitedRegion, "limitedRegion");

        // Paper's Random is intentionally not used: the stable contract is derived from the
        // world seed and reconstructed source chunks, independent of population scheduling.
        APPLICATOR.apply(
                worldInfo.getSeed(),
                chunkX,
                chunkZ,
                worldInfo.getMinHeight(),
                worldInfo.getMaxHeight(),
                new LimitedRegionGeologyBlockAccess(limitedRegion));
    }
}
