package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class LegacyReplaceableBlockTest {
    @Test
    void replacesOnlyOneSixteenFiveNaturalStoneKinds() {
        EnumSet<LegacyBlockKind> replaceable = EnumSet.of(
                LegacyBlockKind.STONE,
                LegacyBlockKind.GRANITE,
                LegacyBlockKind.DIORITE,
                LegacyBlockKind.ANDESITE);
        for (LegacyBlockKind blockKind : LegacyBlockKind.values()) {
            assertEquals(replaceable.contains(blockKind), LegacyReplaceableBlock.isReplaceable(blockKind),
                    () -> "unexpected replacement decision for " + blockKind);
        }
    }
}
