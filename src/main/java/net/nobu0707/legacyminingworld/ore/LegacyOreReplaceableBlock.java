package net.nobu0707.legacyminingworld.ore;

import net.nobu0707.legacyminingworld.geology.LegacyBlockKind;
import net.nobu0707.legacyminingworld.geology.LegacyReplaceableBlock;

/** Natural-stone replacement policy for the Phase 3B ore applicator. */
public final class LegacyOreReplaceableBlock {
    private LegacyOreReplaceableBlock() {
    }

    public static boolean isReplaceable(LegacyBlockKind blockKind) {
        return LegacyReplaceableBlock.isReplaceable(blockKind);
    }
}
