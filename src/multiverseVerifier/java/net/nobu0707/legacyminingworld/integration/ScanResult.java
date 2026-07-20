package net.nobu0707.legacyminingworld.integration;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

record ScanResult(
        Map<Material, Integer> fullCounts,
        Map<Material, Integer> yOneToFourCounts,
        Map<Material, Integer> yFiveToSixtySevenCounts,
        Map<Material, Integer> yElevenCounts,
        Map<ChunkKey, Long> chunkChecksums,
        long yFiveToSixtySevenChecksum,
        int belowZeroAir,
        int yZeroBedrock,
        int ySixtyEightToSixtyNineDirt,
        int ySeventyGrass,
        int aboveSeventyAir,
        int yOneToFourBedrock,
        int yOneToFourNonBedrock,
        int forbiddenCount,
        int biomeChecks,
        Set<Material> unknownNonAir) {
}
