package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import net.nobu0707.legacyminingworld.geology.LegacyBlockKind;
import org.junit.jupiter.api.Test;

class LegacyOreReplaceableBlockTest {
    @Test
    void replacesOnlyNaturalOverworldStoneAndNeverAnExistingOre() {
        EnumSet<LegacyBlockKind> replaceable = EnumSet.of(
                LegacyBlockKind.STONE,
                LegacyBlockKind.GRANITE,
                LegacyBlockKind.DIORITE,
                LegacyBlockKind.ANDESITE);
        for (LegacyBlockKind blockKind : LegacyBlockKind.values()) {
            assertEquals(
                    replaceable.contains(blockKind),
                    LegacyOreReplaceableBlock.isReplaceable(blockKind),
                    blockKind::name);
        }
        assertThrows(NullPointerException.class,
                () -> LegacyOreReplaceableBlock.isReplaceable(null));
    }
}
