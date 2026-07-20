package net.nobu0707.legacyminingworld.geology;

/** Streaming API that Phase 2B can connect to a LimitedRegion adapter. */
@FunctionalInterface
public interface LegacyPlacementSink {
    void accept(LegacyPlacement placement);
}
