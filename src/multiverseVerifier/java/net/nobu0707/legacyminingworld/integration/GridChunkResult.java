package net.nobu0707.legacyminingworld.integration;

import java.util.Map;
import org.bukkit.Material;

record GridChunkResult(
        ChunkKey chunk,
        long yFiveToSixtySevenChecksum,
        long fullChecksum,
        Map<Material, Long> yFiveToSixtySevenCounts) {
}
