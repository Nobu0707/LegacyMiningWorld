package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LegacyVeinGeneratorTest {
    private static final LegacyBlockPosition GOLDEN_ORIGIN = new LegacyBlockPosition(15, 40, 15);

    @Test
    void freezesOfficialShapeCallOrderAndBounds() {
        List<GeologyTestSupport.Candidate> candidates =
                GeologyTestSupport.generate(123_456_789L, GOLDEN_ORIGIN);
        Bounds bounds = Bounds.of(candidates);

        System.out.printf(
                "VEIN_PROBE count=%d checksum=%d bounds=%s first=%s last=%s%n",
                candidates.size(),
                GeologyTestSupport.candidateChecksum(candidates),
                bounds,
                candidates.getFirst(),
                candidates.getLast());
        assertEquals(106, candidates.size());
        assertEquals(9_016_640_837_519_771_701L,
                GeologyTestSupport.candidateChecksum(candidates));
        assertEquals(new Bounds(10, 18, 36, 41, 12, 17), bounds);
        assertEquals(new GeologyTestSupport.Candidate(18, 37, 12, 0), candidates.getFirst());
        assertEquals(new GeologyTestSupport.Candidate(11, 38, 17, 105), candidates.getLast());
        assertEquals(candidates, GeologyTestSupport.generate(123_456_789L, GOLDEN_ORIGIN));
        assertNotEquals(candidates, GeologyTestSupport.generate(123_456_790L, GOLDEN_ORIGIN));
        assertEquals(candidates.size(), new HashSet<>(candidates.stream()
                .map(candidate -> new LegacyBlockPosition(candidate.x(), candidate.y(), candidate.z()))
                .toList()).size());
        for (int index = 0; index < candidates.size(); index++) {
            assertEquals(index, candidates.get(index).sequence());
        }
    }

    @Test
    void preservesRandomStateAfterTheGoldenVein() {
        Random random = new Random(123_456_789L);
        new LegacyVeinGenerator().generate(random, GOLDEN_ORIGIN, 33, (x, y, z, sequence) -> {});
        assertEquals(6_751_368_902_046_319_234L, random.nextLong());
    }

    @Test
    void validatesInputsAndHandlesNegativeCoordinates() {
        LegacyVeinGenerator generator = new LegacyVeinGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.generate(
                new Random(1L), new LegacyBlockPosition(0, 0, 0), 0, (x, y, z, sequence) -> {}));
        List<GeologyTestSupport.Candidate> candidates = GeologyTestSupport.generate(
                Long.MIN_VALUE, new LegacyBlockPosition(-16, 0, -16));
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.x() < 0));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.z() < 0));
    }

    @Test
    void reconstructsBothSidesOfXAndZChunkBoundaries() {
        List<GeologyTestSupport.Candidate> full =
                GeologyTestSupport.generate(123_456_789L, GOLDEN_ORIGIN);
        assertPartitionReconstruction(full, true);
        assertPartitionReconstruction(full, false);

        Set<LegacyBlockPosition> fourChunkUnion = new HashSet<>();
        for (int chunkX = 0; chunkX <= 1; chunkX++) {
            for (int chunkZ = 0; chunkZ <= 1; chunkZ++) {
                int minimumX = chunkX * 16;
                int minimumZ = chunkZ * 16;
                GeologyTestSupport.generate(123_456_789L, GOLDEN_ORIGIN).stream()
                        .filter(candidate -> candidate.x() >= minimumX && candidate.x() < minimumX + 16)
                        .filter(candidate -> candidate.z() >= minimumZ && candidate.z() < minimumZ + 16)
                        .map(candidate -> new LegacyBlockPosition(
                                candidate.x(), candidate.y(), candidate.z()))
                        .forEach(fourChunkUnion::add);
            }
        }
        Set<LegacyBlockPosition> positiveQuadrant = new HashSet<>(full.stream()
                .filter(candidate -> candidate.x() >= 0 && candidate.x() < 32)
                .filter(candidate -> candidate.z() >= 0 && candidate.z() < 32)
                .map(candidate -> new LegacyBlockPosition(candidate.x(), candidate.y(), candidate.z()))
                .toList());
        assertEquals(positiveQuadrant, fourChunkUnion);
    }

    @Test
    void provesOneChunkSourceRadiusForSizeThirtyThree() {
        double maximumEndpointOffset = 33.0D / 8.0D;
        double maximumEllipsoidRadius = (2.0D * (33.0D / 16.0D) + 1.0D) / 2.0D;
        double maximumHorizontalReach = maximumEndpointOffset + maximumEllipsoidRadius;
        assertTrue(maximumHorizontalReach < 7.0D);
        assertTrue(maximumHorizontalReach < 16.5D);
        assertEquals(1, LegacyGeologySettings.SOURCE_NEIGHBORHOOD_RADIUS);
    }

    private static void assertPartitionReconstruction(
            List<GeologyTestSupport.Candidate> full, boolean splitX) {
        Set<LegacyBlockPosition> lower = reconstructSide(splitX, false);
        Set<LegacyBlockPosition> upper = reconstructSide(splitX, true);
        assertFalse(lower.isEmpty());
        assertFalse(upper.isEmpty());
        assertTrue(lower.stream().allMatch(position -> (splitX ? position.x() : position.z()) < 16));
        assertTrue(upper.stream().allMatch(position -> (splitX ? position.x() : position.z()) >= 16));
        Set<LegacyBlockPosition> union = new HashSet<>(lower);
        union.addAll(upper);
        assertEquals(new HashSet<>(full.stream()
                .map(candidate -> new LegacyBlockPosition(candidate.x(), candidate.y(), candidate.z()))
                .toList()), union);
    }

    private static Set<LegacyBlockPosition> reconstructSide(boolean splitX, boolean upper) {
        Set<LegacyBlockPosition> side = new HashSet<>();
        GeologyTestSupport.generate(123_456_789L, GOLDEN_ORIGIN).stream()
                .filter(candidate -> ((splitX ? candidate.x() : candidate.z()) >= 16) == upper)
                .map(candidate -> new LegacyBlockPosition(candidate.x(), candidate.y(), candidate.z()))
                .forEach(side::add);
        return side;
    }

    private record Bounds(int minimumX, int maximumX, int minimumY, int maximumY, int minimumZ, int maximumZ) {
        static Bounds of(List<GeologyTestSupport.Candidate> candidates) {
            return new Bounds(
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::x).min().orElseThrow(),
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::x).max().orElseThrow(),
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::y).min().orElseThrow(),
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::y).max().orElseThrow(),
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::z).min().orElseThrow(),
                    candidates.stream().mapToInt(GeologyTestSupport.Candidate::z).max().orElseThrow());
        }
    }
}
