package net.nobu0707.legacyminingworld.geology;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class GeologyTestSupport {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private GeologyTestSupport() {
    }

    static List<Candidate> generate(long randomSeed, LegacyBlockPosition origin) {
        List<Candidate> candidates = new ArrayList<>();
        new LegacyVeinGenerator().generate(
                new Random(randomSeed),
                origin,
                33,
                (x, y, z, sequence) -> candidates.add(new Candidate(x, y, z, sequence)));
        return List.copyOf(candidates);
    }

    static long placementChecksum(List<LegacyPlacement> placements) {
        long checksum = FNV_OFFSET_BASIS;
        for (LegacyPlacement placement : placements) {
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

    static long candidateChecksum(List<Candidate> candidates) {
        long checksum = FNV_OFFSET_BASIS;
        for (Candidate candidate : candidates) {
            checksum = mix(checksum, candidate.x());
            checksum = mix(checksum, candidate.y());
            checksum = mix(checksum, candidate.z());
            checksum = mix(checksum, candidate.sequence());
        }
        return checksum;
    }

    private static long mix(long checksum, int value) {
        return (checksum ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
    }

    record Candidate(int x, int y, int z, int sequence) {
    }
}
