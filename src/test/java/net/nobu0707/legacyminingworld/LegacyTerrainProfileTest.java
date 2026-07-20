package net.nobu0707.legacyminingworld;

import static net.nobu0707.legacyminingworld.LegacyTerrainProfile.Layer.AIR;
import static net.nobu0707.legacyminingworld.LegacyTerrainProfile.Layer.BEDROCK_FLOOR;
import static net.nobu0707.legacyminingworld.LegacyTerrainProfile.Layer.DIRT;
import static net.nobu0707.legacyminingworld.LegacyTerrainProfile.Layer.GRASS_BLOCK;
import static net.nobu0707.legacyminingworld.LegacyTerrainProfile.Layer.STONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyTerrainProfileTest {
    @Test
    void classifiesEveryTerrainBoundary() {
        assertEquals(AIR, LegacyTerrainProfile.layerAt(-64));
        assertEquals(AIR, LegacyTerrainProfile.layerAt(-1));
        assertEquals(BEDROCK_FLOOR, LegacyTerrainProfile.layerAt(0));
        assertEquals(BEDROCK_FLOOR, LegacyTerrainProfile.layerAt(4));
        assertEquals(STONE, LegacyTerrainProfile.layerAt(5));
        assertEquals(STONE, LegacyTerrainProfile.layerAt(67));
        assertEquals(DIRT, LegacyTerrainProfile.layerAt(68));
        assertEquals(DIRT, LegacyTerrainProfile.layerAt(69));
        assertEquals(GRASS_BLOCK, LegacyTerrainProfile.layerAt(70));
        assertEquals(AIR, LegacyTerrainProfile.layerAt(71));
        assertEquals(AIR, LegacyTerrainProfile.layerAt(319));
    }
}
