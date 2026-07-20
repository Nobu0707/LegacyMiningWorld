package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.nobu0707.legacyminingworld.geology.LegacyBlockPosition;
import net.nobu0707.legacyminingworld.geology.LegacyDecorationSeed;
import net.nobu0707.legacyminingworld.geology.LegacyVeinGenerator;
import org.junit.jupiter.api.Test;

class LegacyOreOriginSequenceTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private static final int SOURCE_CHUNK_X = -3;
    private static final int SOURCE_CHUNK_Z = 4;

    @Test
    void consumesEveryOriginAndShapeInOfficialDecoratorOrder() {
        int totalAttempts = 0;
        for (LegacyOreFeature feature : LegacyOreSettings.FEATURES) {
            FeatureSequence sequence = generateFeatureSequence(feature);
            totalAttempts += sequence.origins().size();
            System.out.printf(
                    "ORE_ORIGIN_PROBE feature=%s first=%s last=%s firstAttemptSentinel=%d finalSentinel=%d attempts=%d%n",
                    feature,
                    sequence.origins().getFirst(),
                    sequence.origins().getLast(),
                    firstAttemptSentinel(feature),
                    sequence.sentinel(),
                    sequence.origins().size());
            ExpectedSequence expected = expected(feature);
            assertEquals(expected.first(), sequence.origins().getFirst());
            assertEquals(expected.last(), sequence.origins().getLast());
            assertEquals(expected.firstAttemptSentinel(), firstAttemptSentinel(feature));
            assertEquals(expected.finalSentinel(), sequence.sentinel());
            assertEquals(feature.attempts(), sequence.origins().size());
            for (Origin origin : sequence.origins()) {
                assertTrue(origin.localX() >= 0 && origin.localX() < 16);
                assertTrue(origin.localZ() >= 0 && origin.localZ() < 16);
                assertTrue(feature.heightDistribution().containsPossibleOriginY(origin.y()));
            }
            assertEquals(sequence, generateFeatureSequence(feature));
        }
        assertEquals(52, totalAttempts);
    }

    private static FeatureSequence generateFeatureSequence(LegacyOreFeature feature) {
        Random random = LegacyDecorationSeed.featureRandom(
                FIXED_SEED,
                SOURCE_CHUNK_X,
                SOURCE_CHUNK_Z,
                feature.stableSalt());
        LegacyVeinGenerator generator = new LegacyVeinGenerator();
        List<Origin> origins = new ArrayList<>();
        for (int attempt = 0; attempt < feature.attempts(); attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
            int y = feature.heightDistribution().sampleY(random);
            origins.add(new Origin(localX, localZ, y));
            generator.generate(
                    random,
                    new LegacyBlockPosition(
                            SOURCE_CHUNK_X * 16 + localX,
                            y,
                            SOURCE_CHUNK_Z * 16 + localZ),
                    feature.veinSize(),
                    (x, candidateY, z, veinSequence) -> {});
        }
        return new FeatureSequence(List.copyOf(origins), random.nextLong());
    }

    private static long firstAttemptSentinel(LegacyOreFeature feature) {
        Random random = LegacyDecorationSeed.featureRandom(
                FIXED_SEED,
                SOURCE_CHUNK_X,
                SOURCE_CHUNK_Z,
                feature.stableSalt());
        int localX = random.nextInt(16);
        int localZ = random.nextInt(16);
        int y = feature.heightDistribution().sampleY(random);
        new LegacyVeinGenerator().generate(
                random,
                new LegacyBlockPosition(
                        SOURCE_CHUNK_X * 16 + localX,
                        y,
                        SOURCE_CHUNK_Z * 16 + localZ),
                feature.veinSize(),
                (x, candidateY, z, veinSequence) -> {});
        return random.nextLong();
    }

    private static ExpectedSequence expected(LegacyOreFeature feature) {
        return switch (feature) {
            case COAL -> new ExpectedSequence(
                    new Origin(8, 6, 49),
                    new Origin(8, 9, 21),
                    -858_603_504_957_224_522L,
                    -6_684_950_609_410_573_795L);
            case IRON -> new ExpectedSequence(
                    new Origin(8, 10, 35),
                    new Origin(3, 2, 20),
                    8_768_211_691_695_374_164L,
                    6_001_840_520_625_018_638L);
            case GOLD -> new ExpectedSequence(
                    new Origin(8, 15, 12),
                    new Origin(3, 3, 21),
                    3_921_826_100_122_031_952L,
                    -969_196_575_712_159_662L);
            case REDSTONE -> new ExpectedSequence(
                    new Origin(8, 4, 9),
                    new Origin(14, 3, 4),
                    -8_554_831_122_079_538_311L,
                    -8_606_727_018_132_100_038L);
            case DIAMOND -> new ExpectedSequence(
                    new Origin(8, 7, 1),
                    new Origin(8, 7, 1),
                    -2_206_617_873_898_950_728L,
                    -2_206_617_873_898_950_728L);
            case LAPIS -> new ExpectedSequence(
                    new Origin(8, 11, 8),
                    new Origin(8, 11, 8),
                    5_947_713_773_268_111_009L,
                    5_947_713_773_268_111_009L);
        };
    }

    private record Origin(int localX, int localZ, int y) {
    }

    private record FeatureSequence(List<Origin> origins, long sentinel) {
    }

    private record ExpectedSequence(
            Origin first, Origin last, long firstAttemptSentinel, long finalSentinel) {
    }
}
