package net.nobu0707.legacyminingworld.geology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Stateless target-chunk-owned planner for reconstructable cross-chunk geology. */
public final class LegacyGeologyPlanner {
    private final LegacyVeinGenerator veinGenerator;

    public LegacyGeologyPlanner() {
        this(new LegacyVeinGenerator());
    }

    LegacyGeologyPlanner(LegacyVeinGenerator veinGenerator) {
        this.veinGenerator = Objects.requireNonNull(veinGenerator, "veinGenerator");
    }

    public List<LegacyPlacement> plan(long worldSeed, int targetChunkX, int targetChunkZ) {
        List<LegacyPlacement> placements = new ArrayList<>();
        plan(worldSeed, targetChunkX, targetChunkZ, placements::add);
        return List.copyOf(placements);
    }

    public void plan(
            long worldSeed,
            int targetChunkX,
            int targetChunkZ,
            LegacyPlacementSink sink) {
        Objects.requireNonNull(sink, "sink");
        int targetMinimumX = targetChunkX << 4;
        int targetMinimumZ = targetChunkZ << 4;
        int targetMaximumXExclusive = targetMinimumX + LegacyGeologySettings.CHUNK_SIZE;
        int targetMaximumZExclusive = targetMinimumZ + LegacyGeologySettings.CHUNK_SIZE;
        int radius = LegacyGeologySettings.SOURCE_NEIGHBORHOOD_RADIUS;

        for (int sourceChunkZ = targetChunkZ - radius;
                sourceChunkZ <= targetChunkZ + radius;
                sourceChunkZ++) {
            for (int sourceChunkX = targetChunkX - radius;
                    sourceChunkX <= targetChunkX + radius;
                    sourceChunkX++) {
                planSourceChunk(
                        worldSeed,
                        sourceChunkX,
                        sourceChunkZ,
                        targetMinimumX,
                        targetMaximumXExclusive,
                        targetMinimumZ,
                        targetMaximumZExclusive,
                        sink);
            }
        }
    }

    private void planSourceChunk(
            long worldSeed,
            int sourceChunkX,
            int sourceChunkZ,
            int targetMinimumX,
            int targetMaximumXExclusive,
            int targetMinimumZ,
            int targetMaximumZExclusive,
            LegacyPlacementSink sink) {
        int sourceMinimumX = sourceChunkX << 4;
        int sourceMinimumZ = sourceChunkZ << 4;
        for (LegacyGeologyFeature feature : LegacyGeologySettings.FEATURES) {
            Random random = LegacyDecorationSeed.featureRandom(
                    worldSeed, sourceChunkX, sourceChunkZ, feature);
            for (int attemptIndex = 0; attemptIndex < feature.attempts(); attemptIndex++) {
                int originX = sourceMinimumX + random.nextInt(LegacyGeologySettings.CHUNK_SIZE);
                int originZ = sourceMinimumZ + random.nextInt(LegacyGeologySettings.CHUNK_SIZE);
                int originY = feature.originMinY()
                        + random.nextInt(feature.originMaxYExclusive() - feature.originMinY());
                int stableAttemptIndex = attemptIndex;
                veinGenerator.generate(
                        random,
                        new LegacyBlockPosition(originX, originY, originZ),
                        feature.veinSize(),
                        (x, y, z, veinSequence) -> {
                            if (x >= targetMinimumX
                                    && x < targetMaximumXExclusive
                                    && z >= targetMinimumZ
                                    && z < targetMaximumZExclusive) {
                                sink.accept(new LegacyPlacement(
                                        x,
                                        y,
                                        z,
                                        feature.material(),
                                        sourceChunkX,
                                        sourceChunkZ,
                                        feature.stableOrder(),
                                        stableAttemptIndex,
                                        veinSequence));
                            }
                        });
            }
        }
    }
}
