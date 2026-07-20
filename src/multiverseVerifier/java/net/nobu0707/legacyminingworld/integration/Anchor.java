package net.nobu0707.legacyminingworld.integration;

import org.bukkit.Material;

record Anchor(
        String id,
        int x,
        int y,
        int z,
        Material expectedMaterial,
        String purpose,
        String pairId,
        int sourceChunkX,
        int sourceChunkZ,
        String feature,
        int attempt,
        int veinSequence) {
    int chunkX() {
        return Math.floorDiv(x, 16);
    }

    int chunkZ() {
        return Math.floorDiv(z, 16);
    }
}
