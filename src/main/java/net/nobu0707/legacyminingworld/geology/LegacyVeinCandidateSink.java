package net.nobu0707.legacyminingworld.geology;

/** Streaming boundary for the pure legacy vein shape generator. */
@FunctionalInterface
public interface LegacyVeinCandidateSink {
    void accept(int x, int y, int z, int veinSequence);
}
