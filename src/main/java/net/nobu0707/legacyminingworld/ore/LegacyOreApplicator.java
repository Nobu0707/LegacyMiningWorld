package net.nobu0707.legacyminingworld.ore;

import java.util.Objects;
import net.nobu0707.legacyminingworld.geology.LegacyBlockKind;

/** Applies stable ore candidates to the current natural-stone state of a target chunk. */
public final class LegacyOreApplicator {
    private final LegacyOrePlanner planner;

    public LegacyOreApplicator() {
        this(new LegacyOrePlanner());
    }

    LegacyOreApplicator(LegacyOrePlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public ApplicationSummary apply(
            long worldSeed,
            int targetChunkX,
            int targetChunkZ,
            int worldMinimumY,
            int worldMaximumYExclusive,
            LegacyOreBlockAccess blockAccess) {
        validateRange(worldMinimumY, worldMaximumYExclusive);
        Objects.requireNonNull(blockAccess, "blockAccess");
        MutableSummary summary = new MutableSummary();
        planner.plan(worldSeed, targetChunkX, targetChunkZ, placement -> applyPlacement(
                placement,
                targetChunkX,
                targetChunkZ,
                worldMinimumY,
                worldMaximumYExclusive,
                blockAccess,
                summary));
        return summary.snapshot();
    }

    ApplicationSummary applyPlacements(
            Iterable<LegacyOrePlacement> placements,
            int targetChunkX,
            int targetChunkZ,
            int worldMinimumY,
            int worldMaximumYExclusive,
            LegacyOreBlockAccess blockAccess) {
        validateRange(worldMinimumY, worldMaximumYExclusive);
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(blockAccess, "blockAccess");
        MutableSummary summary = new MutableSummary();
        for (LegacyOrePlacement placement : placements) {
            applyPlacement(
                    Objects.requireNonNull(placement, "placement"),
                    targetChunkX,
                    targetChunkZ,
                    worldMinimumY,
                    worldMaximumYExclusive,
                    blockAccess,
                    summary);
        }
        return summary.snapshot();
    }

    private static void applyPlacement(
            LegacyOrePlacement placement,
            int targetChunkX,
            int targetChunkZ,
            int worldMinimumY,
            int worldMaximumYExclusive,
            LegacyOreBlockAccess blockAccess,
            MutableSummary summary) {
        summary.planned++;
        if (Math.floorDiv(placement.x(), LegacyOreSettings.CHUNK_SIZE) != targetChunkX
                || Math.floorDiv(placement.z(), LegacyOreSettings.CHUNK_SIZE) != targetChunkZ) {
            summary.skippedOutsideTargetChunk++;
            return;
        }
        if (placement.y() < worldMinimumY || placement.y() >= worldMaximumYExclusive) {
            summary.skippedByWorldHeight++;
            return;
        }
        if (placement.y() < LegacyOreSettings.MINIMUM_APPLICATION_Y
                || placement.y() >= LegacyOreSettings.MAXIMUM_APPLICATION_Y_EXCLUSIVE) {
            summary.skippedOutsideLegacyOreLayer++;
            return;
        }
        if (!blockAccess.isAccessible(placement.x(), placement.y(), placement.z())) {
            summary.skippedOutOfRegion++;
            return;
        }
        LegacyBlockKind current = blockAccess.getBlockKind(
                placement.x(), placement.y(), placement.z());
        if (!LegacyOreReplaceableBlock.isReplaceable(current)) {
            summary.skippedNotReplaceable++;
            return;
        }
        blockAccess.setMaterial(
                placement.x(), placement.y(), placement.z(), placement.material());
        summary.applied++;
    }

    private static void validateRange(int minimumY, int maximumYExclusive) {
        if (minimumY >= maximumYExclusive) {
            throw new IllegalArgumentException("minimumY must be less than maximumYExclusive");
        }
    }

    public record ApplicationSummary(
            int planned,
            int applied,
            int skippedByWorldHeight,
            int skippedOutsideLegacyOreLayer,
            int skippedOutsideTargetChunk,
            int skippedOutOfRegion,
            int skippedNotReplaceable) {
    }

    private static final class MutableSummary {
        private int planned;
        private int applied;
        private int skippedByWorldHeight;
        private int skippedOutsideLegacyOreLayer;
        private int skippedOutsideTargetChunk;
        private int skippedOutOfRegion;
        private int skippedNotReplaceable;

        private ApplicationSummary snapshot() {
            return new ApplicationSummary(
                    planned,
                    applied,
                    skippedByWorldHeight,
                    skippedOutsideLegacyOreLayer,
                    skippedOutsideTargetChunk,
                    skippedOutOfRegion,
                    skippedNotReplaceable);
        }
    }
}
