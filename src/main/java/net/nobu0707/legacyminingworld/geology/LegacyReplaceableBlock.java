package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;

/** Java Edition 1.16.5 natural-stone replacement policy for Phase 2B. */
public final class LegacyReplaceableBlock {
    private LegacyReplaceableBlock() {
    }

    public static boolean isReplaceable(LegacyBlockKind blockKind) {
        return switch (Objects.requireNonNull(blockKind, "blockKind")) {
            case STONE, GRANITE, DIORITE, ANDESITE -> true;
            default -> false;
        };
    }
}
