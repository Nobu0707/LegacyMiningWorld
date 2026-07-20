package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.nobu0707.legacyminingworld.geology.LegacyBlockPosition;
import net.nobu0707.legacyminingworld.geology.LegacyDecorationSeed;
import net.nobu0707.legacyminingworld.geology.LegacyVeinGenerator;
import org.junit.jupiter.api.Test;

class LegacyOreBoundaryTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private final LegacyOrePlanner planner = new LegacyOrePlanner();

    @Test
    void reconstructsOneFixedCoalVeinAcrossTheNegativePositiveXBoundary() {
        assertBoundaryReconstruction(
                LegacyOreFeature.COAL,
                true,
                new ChunkPosition(-1, 0),
                new ChunkPosition(0, 0),
                new VeinKey(0, -1, 5, 0));
    }

    @Test
    void reconstructsOneFixedIronVeinAcrossTheNegativePositiveZBoundary() {
        assertBoundaryReconstruction(
                LegacyOreFeature.IRON,
                false,
                new ChunkPosition(0, -1),
                new ChunkPosition(0, 0),
                new VeinKey(0, 0, 6, 7));
    }

    @Test
    void reconstructsAStableVeinAtTheFourChunkIntersection() {
        List<ChunkPosition> targets = List.of(
                new ChunkPosition(-1, -1),
                new ChunkPosition(0, -1),
                new ChunkPosition(-1, 0),
                new ChunkPosition(0, 0));
        Map<VeinKey, Set<ChunkPosition>> chunksByVein = new HashMap<>();
        for (ChunkPosition target : targets) {
            for (LegacyOrePlacement placement : planner.plan(FIXED_SEED, target.x(), target.z())) {
                chunksByVein.computeIfAbsent(VeinKey.of(placement), ignored -> new HashSet<>())
                        .add(new ChunkPosition(
                                Math.floorDiv(placement.x(), 16),
                                Math.floorDiv(placement.z(), 16)));
            }
        }
        VeinKey expectedKey = new VeinKey(0, 0, 6, 18);
        Set<ChunkPosition> crossingTargets = chunksByVein.get(expectedKey);
        System.out.printf(
                "ORE_FOUR_CHUNK_BOUNDARY seed=%d vein=%s targets=%s%n",
                FIXED_SEED, expectedKey, crossingTargets);
        assertEquals(Set.copyOf(targets), crossingTargets);
    }

    @Test
    void oneChunkRadiusContainsEverySupportedVeinSizeAndRadiusTwoCannotReach() {
        for (LegacyOreFeature feature : LegacyOreSettings.FEATURES) {
            double maximumEndpointOffset = (double) feature.veinSize() / 8.0D;
            double maximumEllipsoidRadius =
                    (2.0D * ((double) feature.veinSize() / 16.0D) + 1.0D) / 2.0D;
            double maximumHorizontalReach = maximumEndpointOffset + maximumEllipsoidRadius;
            assertTrue(maximumHorizontalReach < 16.5D, feature::name);
        }
        double coalReach = 17.0D / 8.0D + (2.0D * (17.0D / 16.0D) + 1.0D) / 2.0D;
        assertEquals(3.6875D, coalReach);
        assertTrue(coalReach < 16.5D);
        assertEquals(1, LegacyOreSettings.SOURCE_NEIGHBORHOOD_RADIUS);
    }

    private void assertBoundaryReconstruction(
            LegacyOreFeature feature,
            boolean splitX,
            ChunkPosition lowerTarget,
            ChunkPosition upperTarget,
            VeinKey key) {
        List<LegacyOrePlacement> lowerFirst = planner.plan(
                FIXED_SEED, lowerTarget.x(), lowerTarget.z());
        List<LegacyOrePlacement> upperSecond = planner.plan(
                FIXED_SEED, upperTarget.x(), upperTarget.z());
        List<LegacyOrePlacement> upperFirst = planner.plan(
                FIXED_SEED, upperTarget.x(), upperTarget.z());
        List<LegacyOrePlacement> lowerSecond = planner.plan(
                FIXED_SEED, lowerTarget.x(), lowerTarget.z());
        assertEquals(lowerFirst, lowerSecond);
        assertEquals(upperSecond, upperFirst);

        List<LegacyOrePlacement> lower = lowerFirst.stream()
                .filter(placement -> placement.material() == feature.material())
                .toList();
        List<LegacyOrePlacement> upper = upperSecond.stream()
                .filter(placement -> placement.material() == feature.material())
                .toList();
        Map<VeinKey, List<LegacyOrePlacement>> byVein = new HashMap<>();
        lower.forEach(placement -> byVein.computeIfAbsent(
                VeinKey.of(placement), ignored -> new ArrayList<>()).add(placement));
        upper.forEach(placement -> byVein.computeIfAbsent(
                VeinKey.of(placement), ignored -> new ArrayList<>()).add(placement));

        Set<Candidate> planned = new HashSet<>(byVein.get(key).stream()
                .map(placement -> new Candidate(
                        placement.x(), placement.y(), placement.z(), placement.veinSequence()))
                .toList());
        Set<Candidate> direct = reconstructVein(feature, key).stream()
                .filter(candidate -> splitX
                        ? candidate.x() >= -16 && candidate.x() < 16
                                && candidate.z() >= 0 && candidate.z() < 16
                        : candidate.x() >= 0 && candidate.x() < 16
                                && candidate.z() >= -16 && candidate.z() < 16)
                .collect(java.util.stream.Collectors.toSet());

        System.out.printf(
                "ORE_BOUNDARY_PROBE seed=%d axis=%s feature=%s vein=%s candidates=%d%n",
                FIXED_SEED, splitX ? "X" : "Z", feature, key, planned.size());
        assertFalse(planned.isEmpty());
        assertTrue(planned.stream().anyMatch(candidate ->
                (splitX ? candidate.x() : candidate.z()) < 0));
        assertTrue(planned.stream().anyMatch(candidate ->
                (splitX ? candidate.x() : candidate.z()) >= 0));
        assertEquals(direct, planned);
    }

    private static List<Candidate> reconstructVein(
            LegacyOreFeature feature, VeinKey selectedKey) {
        Random random = LegacyDecorationSeed.featureRandom(
                FIXED_SEED,
                selectedKey.sourceChunkX(),
                selectedKey.sourceChunkZ(),
                feature.stableSalt());
        LegacyVeinGenerator generator = new LegacyVeinGenerator();
        for (int attempt = 0; attempt <= selectedKey.attemptIndex(); attempt++) {
            int originX = selectedKey.sourceChunkX() * 16 + random.nextInt(16);
            int originZ = selectedKey.sourceChunkZ() * 16 + random.nextInt(16);
            int originY = feature.heightDistribution().sampleY(random);
            List<Candidate> candidates = new ArrayList<>();
            generator.generate(
                    random,
                    new LegacyBlockPosition(originX, originY, originZ),
                    feature.veinSize(),
                    (x, y, z, sequence) -> candidates.add(new Candidate(x, y, z, sequence)));
            if (attempt == selectedKey.attemptIndex()) {
                return List.copyOf(candidates);
            }
        }
        throw new AssertionError("selected attempt was not reconstructed");
    }

    private record ChunkPosition(int x, int z) {
    }

    private record Candidate(int x, int y, int z, int sequence) {
    }

    private record VeinKey(
            int sourceChunkZ,
            int sourceChunkX,
            int featureOrder,
            int attemptIndex) implements Comparable<VeinKey> {
        static VeinKey of(LegacyOrePlacement placement) {
            return new VeinKey(
                    placement.sourceChunkZ(),
                    placement.sourceChunkX(),
                    placement.featureOrder(),
                    placement.attemptIndex());
        }

        @Override
        public int compareTo(VeinKey other) {
            return Comparator.comparingInt(VeinKey::sourceChunkZ)
                    .thenComparingInt(VeinKey::sourceChunkX)
                    .thenComparingInt(VeinKey::featureOrder)
                    .thenComparingInt(VeinKey::attemptIndex)
                    .compare(this, other);
        }
    }
}
