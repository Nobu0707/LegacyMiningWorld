package net.nobu0707.legacyminingworld.geology;

/** A target-chunk-owned placement, including the stable order needed by the Paper adapter. */
public record LegacyPlacement(
        int x,
        int y,
        int z,
        LegacyGeologyMaterial material,
        int sourceChunkX,
        int sourceChunkZ,
        int featureOrder,
        int attemptIndex,
        int veinSequence) {
}
