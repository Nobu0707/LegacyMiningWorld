package net.nobu0707.legacyminingworld.ore;

/** A target-owned ore candidate with stable reconstruction metadata. */
public record LegacyOrePlacement(
        int x,
        int y,
        int z,
        LegacyOreMaterial material,
        int sourceChunkX,
        int sourceChunkZ,
        int featureOrder,
        int attemptIndex,
        int veinSequence) {
}
