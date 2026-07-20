package net.nobu0707.legacyminingworld.ore;

/** Streaming boundary reserved for the Phase 3B Paper applicator. */
@FunctionalInterface
public interface LegacyOrePlacementSink {
    void accept(LegacyOrePlacement placement);
}
