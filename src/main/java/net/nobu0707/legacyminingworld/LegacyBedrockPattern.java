package net.nobu0707.legacyminingworld;

import java.util.Objects;
import java.util.Random;

final class LegacyBedrockPattern {
    private static final int FLOOR_DEPTH = 5;

    private LegacyBedrockPattern() {
    }

    static boolean isBedrock(int y, Random random) {
        if (y < LegacyTerrainProfile.BEDROCK_MIN_Y
                || y > LegacyTerrainProfile.BEDROCK_MAX_Y) {
            return false;
        }
        return y <= Objects.requireNonNull(random, "random").nextInt(FLOOR_DEPTH);
    }
}
