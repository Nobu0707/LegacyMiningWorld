package net.nobu0707.legacyminingworld.ore;

/** Streaming boundary used by inspection and the Phase 3B Paper applicator. */
@FunctionalInterface
public interface LegacyOrePlacementSink {
    void accept(LegacyOrePlacement placement);
}
