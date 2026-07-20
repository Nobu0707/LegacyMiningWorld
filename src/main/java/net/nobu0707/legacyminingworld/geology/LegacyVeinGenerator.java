package net.nobu0707.legacyminingworld.geology;

import java.util.BitSet;
import java.util.Objects;
import java.util.Random;

/** Pure implementation of the Java Edition 1.16.5 ellipsoid-based ore-vein shape. */
public final class LegacyVeinGenerator {
    private static final float PI = (float) Math.PI;
    private static final float LEGACY_SINE_INDEX_SCALE = 10_430.378F;
    private static final int LEGACY_SINE_INDEX_MASK = 65_535;
    private static final double LEGACY_SINE_TABLE_SIZE = 65_536.0D;

    public void generate(
            Random random,
            LegacyBlockPosition origin,
            int veinSize,
            LegacyVeinCandidateSink sink) {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sink, "sink");
        if (veinSize <= 0) {
            throw new IllegalArgumentException("veinSize must be positive");
        }

        float angle = random.nextFloat() * PI;
        float halfSpan = (float) veinSize / 8.0F;
        int padding = ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);

        double startX = origin.x() + Math.sin((double) angle) * halfSpan;
        double endX = origin.x() - Math.sin((double) angle) * halfSpan;
        double startZ = origin.z() + Math.cos((double) angle) * halfSpan;
        double endZ = origin.z() - Math.cos((double) angle) * halfSpan;
        double startY = origin.y() + random.nextInt(3) - 2;
        double endY = origin.y() + random.nextInt(3) - 2;

        int minimumX = origin.x() - ceil(halfSpan) - padding;
        int minimumY = origin.y() - 2 - padding;
        int minimumZ = origin.z() - ceil(halfSpan) - padding;
        int horizontalSize = 2 * (ceil(halfSpan) + padding);
        int verticalSize = 2 * (2 + padding);

        generateEllipsoids(
                random,
                veinSize,
                startX,
                endX,
                startY,
                endY,
                startZ,
                endZ,
                minimumX,
                minimumY,
                minimumZ,
                horizontalSize,
                verticalSize,
                sink);
    }

    private static void generateEllipsoids(
            Random random,
            int veinSize,
            double startX,
            double endX,
            double startY,
            double endY,
            double startZ,
            double endZ,
            int minimumX,
            int minimumY,
            int minimumZ,
            int horizontalSize,
            int verticalSize,
            LegacyVeinCandidateSink sink) {
        double[] ellipsoids = new double[veinSize * 4];
        for (int index = 0; index < veinSize; index++) {
            float progress = (float) index / (float) veinSize;
            double centerX = lerp((double) progress, startX, endX);
            double centerY = lerp((double) progress, startY, endY);
            double centerZ = lerp((double) progress, startZ, endZ);
            double randomScale = random.nextDouble() * (double) veinSize / 16.0D;
            float legacyCurve = legacySin(PI * progress) + 1.0F;
            double radius = ((double) legacyCurve * randomScale + 1.0D) / 2.0D;
            int offset = index * 4;
            ellipsoids[offset] = centerX;
            ellipsoids[offset + 1] = centerY;
            ellipsoids[offset + 2] = centerZ;
            ellipsoids[offset + 3] = radius;
        }

        removeContainedEllipsoids(ellipsoids, veinSize);

        BitSet visited = new BitSet(horizontalSize * verticalSize * horizontalSize);
        int sequence = 0;
        for (int index = 0; index < veinSize; index++) {
            int offset = index * 4;
            double radius = ellipsoids[offset + 3];
            if (radius < 0.0D) {
                continue;
            }
            double centerX = ellipsoids[offset];
            double centerY = ellipsoids[offset + 1];
            double centerZ = ellipsoids[offset + 2];
            int lowerX = Math.max(floor(centerX - radius), minimumX);
            int lowerY = Math.max(floor(centerY - radius), minimumY);
            int lowerZ = Math.max(floor(centerZ - radius), minimumZ);
            int upperX = Math.max(floor(centerX + radius), lowerX);
            int upperY = Math.max(floor(centerY + radius), lowerY);
            int upperZ = Math.max(floor(centerZ + radius), lowerZ);

            for (int x = lowerX; x <= upperX; x++) {
                double normalizedX = ((double) x + 0.5D - centerX) / radius;
                double xSquared = normalizedX * normalizedX;
                if (xSquared >= 1.0D) {
                    continue;
                }
                for (int y = lowerY; y <= upperY; y++) {
                    double normalizedY = ((double) y + 0.5D - centerY) / radius;
                    double xySquared = xSquared + normalizedY * normalizedY;
                    if (xySquared >= 1.0D) {
                        continue;
                    }
                    for (int z = lowerZ; z <= upperZ; z++) {
                        double normalizedZ = ((double) z + 0.5D - centerZ) / radius;
                        if (xySquared + normalizedZ * normalizedZ >= 1.0D) {
                            continue;
                        }
                        int bitIndex = (x - minimumX)
                                + (y - minimumY) * horizontalSize
                                + (z - minimumZ) * horizontalSize * verticalSize;
                        if (visited.get(bitIndex)) {
                            continue;
                        }
                        visited.set(bitIndex);
                        sink.accept(x, y, z, sequence++);
                    }
                }
            }
        }
    }

    private static void removeContainedEllipsoids(double[] ellipsoids, int veinSize) {
        for (int first = 0; first < veinSize - 1; first++) {
            int firstOffset = first * 4;
            if (ellipsoids[firstOffset + 3] <= 0.0D) {
                continue;
            }
            for (int second = first + 1; second < veinSize; second++) {
                int secondOffset = second * 4;
                if (ellipsoids[secondOffset + 3] <= 0.0D) {
                    continue;
                }
                double deltaX = ellipsoids[firstOffset] - ellipsoids[secondOffset];
                double deltaY = ellipsoids[firstOffset + 1] - ellipsoids[secondOffset + 1];
                double deltaZ = ellipsoids[firstOffset + 2] - ellipsoids[secondOffset + 2];
                double deltaRadius = ellipsoids[firstOffset + 3] - ellipsoids[secondOffset + 3];
                if (deltaRadius * deltaRadius
                        > deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) {
                    if (deltaRadius > 0.0D) {
                        ellipsoids[secondOffset + 3] = -1.0D;
                    } else {
                        ellipsoids[firstOffset + 3] = -1.0D;
                    }
                }
            }
        }
    }

    private static float legacySin(float value) {
        int index = (int) (value * LEGACY_SINE_INDEX_SCALE) & LEGACY_SINE_INDEX_MASK;
        return (float) Math.sin((double) index * Math.PI * 2.0D / LEGACY_SINE_TABLE_SIZE);
    }

    private static double lerp(double progress, double start, double end) {
        return start + progress * (end - start);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < (double) truncated ? truncated - 1 : truncated;
    }

    private static int ceil(float value) {
        int truncated = (int) value;
        return value > (float) truncated ? truncated + 1 : truncated;
    }
}
