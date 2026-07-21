package net.nobu0707.legacyminingworld.integration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Biome;

final class GridAccumulator {
    static final List<Material> REPORT_MATERIALS = List.of(
            Material.STONE,
            Material.DIRT,
            Material.GRAVEL,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DIAMOND_ORE,
            Material.LAPIS_ORE);
    static final List<Material> ORES = List.of(
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DIAMOND_ORE,
            Material.LAPIS_ORE);
    private static final List<Integer> BIOME_HEIGHTS = List.of(0, 11, 70, 100);

    private final LargeScaleGridSpec spec;
    private final Map<Material, Long> fullCounts = new EnumMap<>(Material.class);
    private final Map<Material, Long> yFiveCounts = new EnumMap<>(Material.class);
    private final Map<Material, Long> yElevenCounts = new EnumMap<>(Material.class);
    private final Map<Material, long[]> oreHistogram = new EnumMap<>(Material.class);
    private final List<GridChunkResult> chunks = new ArrayList<>();
    private long combinedYFiveChecksum = ScanChecksum.OFFSET_BASIS;
    private long combinedFullChecksum = ScanChecksum.OFFSET_BASIS;
    private long scannedBlocks;
    private long belowZeroAir;
    private long yZeroBedrock;
    private long yOneToFour;
    private long yOneToFourBedrock;
    private long yOneToFourNonBedrock;
    private long ySixtyEightToSixtyNineDirt;
    private long ySeventyGrass;
    private long aboveSeventyAir;
    private long forbidden;
    private long unknownNonAir;
    private long biomeChecks;

    GridAccumulator(LargeScaleGridSpec spec) {
        this.spec = spec;
        for (Material ore : ORES) {
            oreHistogram.put(ore, new long[68]);
        }
    }

    void scan(ChunkSnapshot snapshot, ChunkKey chunk) {
        Map<Material, Long> chunkCounts = new EnumMap<>(Material.class);
        long chunkYFiveChecksum = ScanChecksum.OFFSET_BASIS;
        long chunkFullChecksum = ScanChecksum.OFFSET_BASIS;
        for (int y = spec.minimumY(); y < spec.maximumYExclusive(); y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int x = chunk.x() * 16 + localX;
                    int z = chunk.z() * 16 + localZ;
                    Material material = snapshot.getBlockType(localX, y, localZ);
                    scannedBlocks++;
                    fullCounts.merge(material, 1L, Long::sum);
                    chunkFullChecksum = ScanChecksum.mixBlock(chunkFullChecksum, x, y, z, material);
                    combinedFullChecksum = ScanChecksum.mixBlock(
                            combinedFullChecksum, x, y, z, material);
                    if (SnapshotScanner.FORBIDDEN.contains(material)) {
                        forbidden++;
                    }
                    if (material != Material.AIR
                            && !SnapshotScanner.ALLOWED_NON_AIR.contains(material)) {
                        unknownNonAir++;
                    }
                    validateLayer(material, x, y, z);
                    if (y >= 5 && y <= 67) {
                        yFiveCounts.merge(material, 1L, Long::sum);
                        chunkCounts.merge(material, 1L, Long::sum);
                        chunkYFiveChecksum = ScanChecksum.mixBlock(
                                chunkYFiveChecksum, x, y, z, material);
                        combinedYFiveChecksum = ScanChecksum.mixBlock(
                                combinedYFiveChecksum, x, y, z, material);
                    }
                    if (y == 11) {
                        yElevenCounts.merge(material, 1L, Long::sum);
                    }
                    if (y >= 0 && y <= 67 && oreHistogram.containsKey(material)) {
                        oreHistogram.get(material)[y]++;
                    }
                }
            }
        }
        for (int y : BIOME_HEIGHTS) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    if (snapshot.getBiome(localX, y, localZ) != Biome.PLAINS) {
                        throw new VerificationException("non_plains_biome_"
                                + chunk + "_" + localX + "_" + y + "_" + localZ);
                    }
                    biomeChecks++;
                }
            }
        }
        chunks.add(new GridChunkResult(
                chunk, chunkYFiveChecksum, chunkFullChecksum, Map.copyOf(chunkCounts)));
    }

    private void validateLayer(Material material, int x, int y, int z) {
        boolean valid;
        if (y < 0) {
            valid = material == Material.AIR;
            if (valid) belowZeroAir++;
        } else if (y == 0) {
            valid = material == Material.BEDROCK;
            if (valid) yZeroBedrock++;
        } else if (y <= 4) {
            yOneToFour++;
            valid = SnapshotScanner.Y_ONE_TO_FOUR_ALLOWED.contains(material);
            if (material == Material.BEDROCK) yOneToFourBedrock++;
            else yOneToFourNonBedrock++;
        } else if (y <= 67) {
            valid = REPORT_MATERIALS.contains(material);
        } else if (y <= 69) {
            valid = material == Material.DIRT;
            if (valid) ySixtyEightToSixtyNineDirt++;
        } else if (y == 70) {
            valid = material == Material.GRASS_BLOCK;
            if (valid) ySeventyGrass++;
        } else {
            valid = material == Material.AIR;
            if (valid) aboveSeventyAir++;
        }
        if (!valid) {
            throw new VerificationException("invalid_layer_material_"
                    + x + "_" + y + "_" + z + "_" + material);
        }
    }

    void validateComplete() {
        require(chunks.size() == spec.chunkCount(), "scanned_chunk_count");
        require(scannedBlocks == spec.blockCount(), "scanned_block_count");
        require(belowZeroAir == 17_842_176L, "below_zero_air_count");
        require(yZeroBedrock == 278_784L, "y_zero_bedrock_count");
        require(yOneToFour == 1_115_136L, "y_one_to_four_count");
        require(yOneToFourBedrock > 0 && yOneToFourNonBedrock > 0, "bedrock_mixture");
        require(yFiveCounts.values().stream().mapToLong(Long::longValue).sum()
                == 17_563_392L, "y_five_to_sixty_seven_count");
        require(ySixtyEightToSixtyNineDirt == 557_568L, "surface_dirt_count");
        require(ySeventyGrass == 278_784L, "surface_grass_count");
        require(aboveSeventyAir == 69_417_216L, "above_surface_air_count");
        require(forbidden == 0, "forbidden_material_count");
        require(unknownNonAir == 0, "unknown_non_air_count");
        require(biomeChecks == 1_115_136L, "biome_count");
        for (Material material : REPORT_MATERIALS) {
            require(yFiveCounts.getOrDefault(material, 0L) > 0,
                    "missing_y5_material_" + material);
        }
        for (Material material : ORES.subList(0, 5)) {
            require(yElevenCounts.getOrDefault(material, 0L) > 0,
                    "missing_y11_ore_" + material);
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new VerificationException(reason);
    }

    LargeScaleGridSpec spec() { return spec; }
    Map<Material, Long> fullCounts() { return Map.copyOf(fullCounts); }
    Map<Material, Long> yFiveCounts() { return Map.copyOf(yFiveCounts); }
    Map<Material, Long> yElevenCounts() { return Map.copyOf(yElevenCounts); }
    Map<Material, long[]> oreHistogram() {
        Map<Material, long[]> copy = new EnumMap<>(Material.class);
        oreHistogram.forEach((key, value) -> copy.put(key, value.clone()));
        return copy;
    }
    List<GridChunkResult> chunks() { return List.copyOf(chunks); }
    long combinedYFiveChecksum() { return combinedYFiveChecksum; }
    long combinedFullChecksum() { return combinedFullChecksum; }
    long scannedBlocks() { return scannedBlocks; }
    long belowZeroAir() { return belowZeroAir; }
    long yZeroBedrock() { return yZeroBedrock; }
    long yOneToFour() { return yOneToFour; }
    long yOneToFourBedrock() { return yOneToFourBedrock; }
    long yOneToFourNonBedrock() { return yOneToFourNonBedrock; }
    long ySixtyEightToSixtyNineDirt() { return ySixtyEightToSixtyNineDirt; }
    long ySeventyGrass() { return ySeventyGrass; }
    long aboveSeventyAir() { return aboveSeventyAir; }
    long forbidden() { return forbidden; }
    long unknownNonAir() { return unknownNonAir; }
    long biomeChecks() { return biomeChecks; }
}
