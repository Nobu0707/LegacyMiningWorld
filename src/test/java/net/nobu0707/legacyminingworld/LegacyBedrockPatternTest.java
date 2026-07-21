package net.nobu0707.legacyminingworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LegacyBedrockPatternTest {
    @Test
    void fullyEnumeratesLegacyBedrockDecisionTable() {
        for (int y = 0; y <= 4; y++) {
            for (int randomValue = 0; randomValue <= 4; randomValue++) {
                FixedRandom random = new FixedRandom(randomValue);
                assertEquals(y <= randomValue, LegacyBedrockPattern.isBedrock(y, random),
                        "unexpected result for y=" + y + ", randomValue=" + randomValue);
                assertEquals(1, random.calls, "each floor block must consume one random value");
            }
        }
    }

    @Test
    void coordinatesOutsideFloorAreRejectedWithoutConsumingRandom() {
        FixedRandom random = new FixedRandom(4);
        assertFalse(LegacyBedrockPattern.isBedrock(-1, random));
        assertFalse(LegacyBedrockPattern.isBedrock(5, random));
        assertEquals(0, random.calls);
    }

    private static final class FixedRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final int value;
        private int calls;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            assertEquals(5, bound);
            calls++;
            return value;
        }
    }
}
