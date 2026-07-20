package net.nobu0707.legacyminingworld.integration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;

final class SnapshotScanner {
    static final List<ChunkKey> CHUNKS = List.of(
            new ChunkKey(-1, -1),
            new ChunkKey(0, -1),
            new ChunkKey(-1, 0),
            new ChunkKey(0, 0));
    static final Set<Material> ALLOWED_NON_AIR = Set.of(
            Material.BEDROCK,
            Material.STONE,
            Material.DIRT,
            Material.GRAVEL,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.GRASS_BLOCK,
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DIAMOND_ORE,
            Material.LAPIS_ORE);
    static final Set<Material> Y_ONE_TO_FOUR_ALLOWED = Set.of(
            Material.BEDROCK,
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
    static final Set<Material> FORBIDDEN = Set.of(
            Material.WATER,
            Material.LAVA,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.DEEPSLATE,
            Material.TUFF,
            Material.CALCITE,
            Material.COPPER_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS);
    static final Map<Material, Integer> EXPECTED_Y_FIVE_TO_SIXTY_SEVEN = Map.ofEntries(
            Map.entry(Material.STONE, 51_999),
            Map.entry(Material.DIRT, 995),
            Map.entry(Material.GRAVEL, 890),
            Map.entry(Material.GRANITE, 3_013),
            Map.entry(Material.DIORITE, 2_867),
            Map.entry(Material.ANDESITE, 3_279),
            Map.entry(Material.COAL_ORE, 860),
            Map.entry(Material.IRON_ORE, 438),
            Map.entry(Material.GOLD_ORE, 41),
            Map.entry(Material.REDSTONE_ORE, 94),
            Map.entry(Material.DIAMOND_ORE, 17),
            Map.entry(Material.LAPIS_ORE, 19));
    static final Map<ChunkKey, Long> EXPECTED_CHUNK_CHECKSUMS = Map.of(
            new ChunkKey(-1, -1), -4_081_461_885_369_063_153L,
            new ChunkKey(0, -1), -6_459_175_142_289_166_354L,
            new ChunkKey(-1, 0), 4_995_189_412_391_713_686L,
            new ChunkKey(0, 0), 124_016_103_469_303_630L);
    static final long EXPECTED_COMBINED_CHECKSUM = -7_305_870_198_059_528_782L;
    static final Map<Material, Integer> EXPECTED_Y_ELEVEN = Map.ofEntries(
            Map.entry(Material.STONE, 733),
            Map.entry(Material.DIRT, 47),
            Map.entry(Material.GRAVEL, 19),
            Map.entry(Material.GRANITE, 76),
            Map.entry(Material.DIORITE, 34),
            Map.entry(Material.ANDESITE, 85),
            Map.entry(Material.COAL_ORE, 6),
            Map.entry(Material.IRON_ORE, 5),
            Map.entry(Material.GOLD_ORE, 8),
            Map.entry(Material.REDSTONE_ORE, 7),
            Map.entry(Material.DIAMOND_ORE, 4));
    private static final List<Integer> BIOME_HEIGHTS = List.of(0, 11, 70, 100);

    ScanResult scan(World world, List<Anchor> geologyAnchors, List<Anchor> oreAnchors) {
        Map<ChunkKey, ChunkSnapshot> snapshots = loadSnapshots(world);
        verifyAnchors(snapshots, geologyAnchors);
        verifyAnchors(snapshots, oreAnchors);

        Map<Material, Integer> fullCounts = new EnumMap<>(Material.class);
        Map<Material, Integer> yOneToFourCounts = new EnumMap<>(Material.class);
        Map<Material, Integer> yFiveToSixtySevenCounts = new EnumMap<>(Material.class);
        Map<Material, Integer> yElevenCounts = new EnumMap<>(Material.class);
        Map<ChunkKey, Long> chunkChecksums = new LinkedHashMap<>();
        Set<Material> unknownNonAir = new LinkedHashSet<>();
        long combinedChecksum = ScanChecksum.OFFSET_BASIS;
        int belowZeroAir = 0;
        int yZeroBedrock = 0;
        int ySixtyEightToSixtyNineDirt = 0;
        int ySeventyGrass = 0;
        int aboveSeventyAir = 0;
        int yOneToFourBedrock = 0;
        int yOneToFourNonBedrock = 0;
        int forbiddenCount = 0;
        int biomeChecks = 0;

        for (ChunkKey chunkKey : CHUNKS) {
            ChunkSnapshot snapshot = snapshots.get(chunkKey);
            long chunkChecksum = ScanChecksum.OFFSET_BASIS;
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        Material material = snapshot.getBlockType(localX, y, localZ);
                        int x = chunkKey.x() * 16 + localX;
                        int z = chunkKey.z() * 16 + localZ;
                        fullCounts.merge(material, 1, Integer::sum);
                        if (FORBIDDEN.contains(material)) {
                            forbiddenCount++;
                        }
                        if (material != Material.AIR && !ALLOWED_NON_AIR.contains(material)) {
                            unknownNonAir.add(material);
                        }
                        if (y < 0 && material == Material.AIR) {
                            belowZeroAir++;
                        } else if (y == 0 && material == Material.BEDROCK) {
                            yZeroBedrock++;
                        } else if (y >= 1 && y <= 4) {
                            yOneToFourCounts.merge(material, 1, Integer::sum);
                            if (material == Material.BEDROCK) {
                                yOneToFourBedrock++;
                            } else {
                                yOneToFourNonBedrock++;
                            }
                        } else if (y >= 5 && y <= 67) {
                            yFiveToSixtySevenCounts.merge(material, 1, Integer::sum);
                            chunkChecksum = ScanChecksum.mixBlock(
                                    chunkChecksum, x, y, z, material);
                            combinedChecksum = ScanChecksum.mixBlock(
                                    combinedChecksum, x, y, z, material);
                        } else if ((y == 68 || y == 69) && material == Material.DIRT) {
                            ySixtyEightToSixtyNineDirt++;
                        } else if (y == 70 && material == Material.GRASS_BLOCK) {
                            ySeventyGrass++;
                        } else if (y >= 71 && material == Material.AIR) {
                            aboveSeventyAir++;
                        }
                        if (y == 11) {
                            yElevenCounts.merge(material, 1, Integer::sum);
                        }
                    }
                }
            }
            chunkChecksums.put(chunkKey, chunkChecksum);
            for (int y : BIOME_HEIGHTS) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (snapshot.getBiome(localX, y, localZ) != Biome.PLAINS) {
                            throw new VerificationException("non_plains_biome_"
                                    + chunkKey + "_" + localX + "_" + y + "_" + localZ);
                        }
                        biomeChecks++;
                    }
                }
            }
        }

        return new ScanResult(
                Map.copyOf(fullCounts),
                Map.copyOf(yOneToFourCounts),
                Map.copyOf(yFiveToSixtySevenCounts),
                Map.copyOf(yElevenCounts),
                Map.copyOf(chunkChecksums),
                combinedChecksum,
                belowZeroAir,
                yZeroBedrock,
                ySixtyEightToSixtyNineDirt,
                ySeventyGrass,
                aboveSeventyAir,
                yOneToFourBedrock,
                yOneToFourNonBedrock,
                forbiddenCount,
                biomeChecks,
                Set.copyOf(unknownNonAir));
    }

    private static Map<ChunkKey, ChunkSnapshot> loadSnapshots(World world) {
        Map<ChunkKey, ChunkSnapshot> snapshots = new LinkedHashMap<>();
        for (ChunkKey chunkKey : CHUNKS) {
            Chunk chunk = world.getChunkAt(chunkKey.x(), chunkKey.z());
            if (!chunk.isLoaded() && !chunk.load(true)) {
                throw new VerificationException("chunk_load_failed_" + chunkKey);
            }
            if (!chunk.isGenerated()) {
                throw new VerificationException("chunk_not_generated_" + chunkKey);
            }
            snapshots.put(chunkKey, chunk.getChunkSnapshot(true, true, false, false));
        }
        return Map.copyOf(snapshots);
    }

    private static void verifyAnchors(
            Map<ChunkKey, ChunkSnapshot> snapshots, List<Anchor> anchors) {
        for (Anchor anchor : anchors) {
            ChunkSnapshot snapshot = snapshots.get(new ChunkKey(anchor.chunkX(), anchor.chunkZ()));
            if (snapshot == null) {
                throw new VerificationException("anchor_outside_scan_" + anchor.id());
            }
            Material actual = snapshot.getBlockType(
                    Math.floorMod(anchor.x(), 16), anchor.y(), Math.floorMod(anchor.z(), 16));
            if (actual != anchor.expectedMaterial()) {
                throw new VerificationException("anchor_mismatch_" + anchor.id()
                        + "_expected_" + anchor.expectedMaterial() + "_actual_" + actual);
            }
        }
    }
}
