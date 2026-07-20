package net.nobu0707.legacyminingworld.ore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.nobu0707.legacyminingworld.geology.LegacyBlockPosition;
import net.nobu0707.legacyminingworld.geology.LegacyDecorationSeed;
import net.nobu0707.legacyminingworld.geology.LegacyVeinGenerator;

/** Stateless target-chunk planner for reconstructable Java 1.16.5-style ore veins. */
public final class LegacyOrePlanner {
    private final LegacyVeinGenerator veinGenerator;

    public LegacyOrePlanner() {
        this(new LegacyVeinGenerator());
    }

    LegacyOrePlanner(LegacyVeinGenerator veinGenerator) {
        this.veinGenerator = Objects.requireNonNull(veinGenerator, "veinGenerator");
    }

    public List<LegacyOrePlacement> plan(long worldSeed, int targetChunkX, int targetChunkZ) {
        List<LegacyOrePlacement> placements = new ArrayList<>();
        plan(worldSeed, targetChunkX, targetChunkZ, placements::add);
        return List.copyOf(placements);
    }

    public void plan(
            long worldSeed,
            int targetChunkX,
            int targetChunkZ,
            LegacyOrePlacementSink sink) {
        Objects.requireNonNull(sink, "sink");
        int targetMinimumX = blockMinimum(targetChunkX);
        int targetMinimumZ = blockMinimum(targetChunkZ);
        int targetMaximumXExclusive = Math.addExact(targetMinimumX, LegacyOreSettings.CHUNK_SIZE);
        int targetMaximumZExclusive = Math.addExact(targetMinimumZ, LegacyOreSettings.CHUNK_SIZE);
        int radius = LegacyOreSettings.SOURCE_NEIGHBORHOOD_RADIUS;

        long minimumSourceZ = (long) targetChunkZ - radius;
        long maximumSourceZ = (long) targetChunkZ + radius;
        long minimumSourceX = (long) targetChunkX - radius;
        long maximumSourceX = (long) targetChunkX + radius;
        for (long sourceChunkZValue = minimumSourceZ;
                sourceChunkZValue <= maximumSourceZ;
                sourceChunkZValue++) {
            int sourceChunkZ = Math.toIntExact(sourceChunkZValue);
            for (long sourceChunkXValue = minimumSourceX;
                    sourceChunkXValue <= maximumSourceX;
                    sourceChunkXValue++) {
                int sourceChunkX = Math.toIntExact(sourceChunkXValue);
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
            LegacyOrePlacementSink sink) {
        int sourceMinimumX = blockMinimum(sourceChunkX);
        int sourceMinimumZ = blockMinimum(sourceChunkZ);
        for (LegacyOreFeature feature : LegacyOreSettings.FEATURES) {
            Random random = LegacyDecorationSeed.featureRandom(
                    worldSeed, sourceChunkX, sourceChunkZ, feature.stableSalt());
            for (int attemptIndex = 0; attemptIndex < feature.attempts(); attemptIndex++) {
                int originX = sourceMinimumX + random.nextInt(LegacyOreSettings.CHUNK_SIZE);
                int originZ = sourceMinimumZ + random.nextInt(LegacyOreSettings.CHUNK_SIZE);
                int originY = feature.heightDistribution().sampleY(random);
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
                                sink.accept(new LegacyOrePlacement(
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

    private static int blockMinimum(int chunkCoordinate) {
        return Math.multiplyExact(chunkCoordinate, LegacyOreSettings.CHUNK_SIZE);
    }
}
