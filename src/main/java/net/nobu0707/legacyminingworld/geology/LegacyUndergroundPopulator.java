package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;
import java.util.Random;
import net.nobu0707.legacyminingworld.ore.LegacyOreApplicator;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/** Stateless Paper entry point that applies legacy geology before legacy ores. */
public final class LegacyUndergroundPopulator extends BlockPopulator {
    private static final LegacyGeologyApplicator GEOLOGY_APPLICATOR =
            new LegacyGeologyApplicator();
    private static final LegacyOreApplicator ORE_APPLICATOR = new LegacyOreApplicator();

    @Override
    public void populate(
            WorldInfo worldInfo,
            Random random,
            int chunkX,
            int chunkZ,
            LimitedRegion limitedRegion) {
        Objects.requireNonNull(worldInfo, "worldInfo");
        LegacyUndergroundBlockAccess blockAccess =
                new LimitedRegionUndergroundBlockAccess(limitedRegion);

        // Paper's Random is intentionally not consumed. WorldInfo's seed and reconstructed
        // source chunks keep results independent of population scheduling and thread order.
        applyUnderground(
                worldInfo.getSeed(),
                chunkX,
                chunkZ,
                worldInfo.getMinHeight(),
                worldInfo.getMaxHeight(),
                blockAccess);
    }

    static PopulationSummary applyUnderground(
            long worldSeed,
            int targetChunkX,
            int targetChunkZ,
            int worldMinimumY,
            int worldMaximumYExclusive,
            LegacyUndergroundBlockAccess blockAccess) {
        Objects.requireNonNull(blockAccess, "blockAccess");
        LegacyGeologyApplicator.ApplicationSummary geology = GEOLOGY_APPLICATOR.apply(
                worldSeed,
                targetChunkX,
                targetChunkZ,
                worldMinimumY,
                worldMaximumYExclusive,
                blockAccess);
        LegacyOreApplicator.ApplicationSummary ore = ORE_APPLICATOR.apply(
                worldSeed,
                targetChunkX,
                targetChunkZ,
                worldMinimumY,
                worldMaximumYExclusive,
                blockAccess);
        return new PopulationSummary(geology, ore);
    }

    record PopulationSummary(
            LegacyGeologyApplicator.ApplicationSummary geology,
            LegacyOreApplicator.ApplicationSummary ore) {
    }
}
