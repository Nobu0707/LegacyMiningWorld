package net.nobu0707.legacyminingworld;

final class LegacyTerrainProfile {
    static final int BEDROCK_MIN_Y = 0;
    static final int BEDROCK_MAX_Y = 4;
    static final int STONE_MIN_Y = 5;
    static final int STONE_MAX_Y = 67;
    static final int DIRT_MIN_Y = 68;
    static final int DIRT_MAX_Y = 69;
    static final int SURFACE_Y = 70;

    private LegacyTerrainProfile() {
    }

    static Layer layerAt(int y) {
        if (y < BEDROCK_MIN_Y || y > SURFACE_Y) {
            return Layer.AIR;
        }
        if (y <= BEDROCK_MAX_Y) {
            return Layer.BEDROCK_FLOOR;
        }
        if (y <= STONE_MAX_Y) {
            return Layer.STONE;
        }
        if (y <= DIRT_MAX_Y) {
            return Layer.DIRT;
        }
        return Layer.GRASS_BLOCK;
    }

    enum Layer {
        AIR,
        BEDROCK_FLOOR,
        STONE,
        DIRT,
        GRASS_BLOCK
    }
}
