package net.nobu0707.legacyminingworld.ore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class OreTestSupport {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private OreTestSupport() {
    }

    static long placementChecksum(List<LegacyOrePlacement> placements) {
        long checksum = FNV_OFFSET_BASIS;
        for (LegacyOrePlacement placement : placements) {
            checksum = mix(checksum, placement.x());
            checksum = mix(checksum, placement.y());
            checksum = mix(checksum, placement.z());
            checksum = mix(checksum, placement.material().ordinal());
            checksum = mix(checksum, placement.sourceChunkX());
            checksum = mix(checksum, placement.sourceChunkZ());
            checksum = mix(checksum, placement.featureOrder());
            checksum = mix(checksum, placement.attemptIndex());
            checksum = mix(checksum, placement.veinSequence());
        }
        return checksum;
    }

    static Map<LegacyOreMaterial, Long> materialCounts(List<LegacyOrePlacement> placements) {
        Map<LegacyOreMaterial, Long> counts = new EnumMap<>(LegacyOreMaterial.class);
        placements.forEach(placement -> counts.merge(placement.material(), 1L, Long::sum));
        return Map.copyOf(counts);
    }

    static Bounds bounds(List<LegacyOrePlacement> placements) {
        return new Bounds(
                placements.stream().mapToInt(LegacyOrePlacement::x).min().orElseThrow(),
                placements.stream().mapToInt(LegacyOrePlacement::x).max().orElseThrow(),
                placements.stream().mapToInt(LegacyOrePlacement::y).min().orElseThrow(),
                placements.stream().mapToInt(LegacyOrePlacement::y).max().orElseThrow(),
                placements.stream().mapToInt(LegacyOrePlacement::z).min().orElseThrow(),
                placements.stream().mapToInt(LegacyOrePlacement::z).max().orElseThrow());
    }

    private static long mix(long checksum, int value) {
        return (checksum ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
    }

    record Bounds(
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ) {
    }
}
