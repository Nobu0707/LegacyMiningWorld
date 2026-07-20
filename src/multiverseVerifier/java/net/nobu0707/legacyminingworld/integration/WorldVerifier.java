package net.nobu0707.legacyminingworld.integration;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

final class WorldVerifier {
    private static final String EXPECTED_GENERATOR =
            "net.nobu0707.legacyminingworld.LegacyMiningChunkGenerator";
    private static final String EXPECTED_BIOME_PROVIDER =
            "net.nobu0707.legacyminingworld.PlainsBiomeProvider";
    private static final int EXPECTED_MINIMUM_Y = -64;
    private static final int EXPECTED_MAXIMUM_Y_EXCLUSIVE = 320;
    private final SnapshotScanner scanner = new SnapshotScanner();

    VerificationSummary verify(
            World world, String expectedWorldName, long expectedSeed, Consumer<String> output)
            throws IOException {
        require(world.getName().equals(expectedWorldName), "world_name");
        require(world.getSeed() == expectedSeed, "world_seed");
        require(world.getEnvironment() == World.Environment.NORMAL, "environment");
        require(world.getMinHeight() == EXPECTED_MINIMUM_Y, "minimum_height");
        require(world.getMaxHeight() == EXPECTED_MAXIMUM_Y_EXCLUSIVE, "maximum_height");
        require(world.getWorldFolder().getName().equals(expectedWorldName), "world_folder");

        ChunkGenerator generator = world.getGenerator();
        require(generator != null, "generator_null");
        require(generator.getClass().getName().equals(EXPECTED_GENERATOR), "generator_class");
        BiomeProvider biomeProvider = world.getBiomeProvider();
        require(biomeProvider != null, "biome_provider_null");
        require(biomeProvider.getClass().getName().equals(EXPECTED_BIOME_PROVIDER),
                "biome_provider_class");
        Location spawn = world.getSpawnLocation();
        require(spawn.getWorld() == world, "spawn_world");
        require(spawn.getX() == 0.0 && spawn.getY() == 71.0 && spawn.getZ() == 0.0,
                "spawn_location_" + spawn.getX() + "_" + spawn.getY() + "_" + spawn.getZ());
        Location fixedSpawn = generator.getFixedSpawnLocation(world, new Random(expectedSeed));
        require(fixedSpawn != null, "fixed_spawn_null");
        require(fixedSpawn.getWorld() == world, "fixed_spawn_world");
        require(fixedSpawn.getX() == 0.5
                        && fixedSpawn.getY() == 71.0
                        && fixedSpawn.getZ() == 0.5,
                "fixed_spawn_location_" + fixedSpawn.getX() + "_"
                        + fixedSpawn.getY() + "_" + fixedSpawn.getZ());

        UUID uid = world.getUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", world.getName());
        metadata.put("uid", uid);
        metadata.put("seed", world.getSeed());
        metadata.put("environment", world.getEnvironment());
        metadata.put("min", world.getMinHeight());
        metadata.put("max", world.getMaxHeight());
        metadata.put("generator", generator.getClass().getName());
        metadata.put("biomeProvider", biomeProvider.getClass().getName());
        metadata.put("spawn", spawn.getX() + "," + spawn.getY() + "," + spawn.getZ());
        metadata.put("fixedSpawn", fixedSpawn.getX() + ","
                + fixedSpawn.getY() + "," + fixedSpawn.getZ());
        metadata.put("folder", world.getWorldFolder().getName());
        output.accept(VerifierSupport.marker("LMW_MV_WORLD_META", metadata));

        List<Anchor> geologyAnchors = AnchorTable.loadRequiredResource(
                WorldVerifier.class, "/geology-smoke-anchors.tsv");
        List<Anchor> oreAnchors = AnchorTable.loadRequiredResource(
                WorldVerifier.class, "/ore-smoke-anchors.tsv");
        validateAnchorMetadata(geologyAnchors, oreAnchors);
        ScanResult scan = scanner.scan(world, geologyAnchors, oreAnchors);
        validateScan(scan);

        output.accept("LMW_MV_TERRAIN_PASS belowAir=" + scan.belowZeroAir()
                + " y0Bedrock=" + scan.yZeroBedrock()
                + " y1_4Bedrock=" + scan.yOneToFourBedrock()
                + " y1_4NonBedrock=" + scan.yOneToFourNonBedrock()
                + " y68_69Dirt=" + scan.ySixtyEightToSixtyNineDirt()
                + " y70Grass=" + scan.ySeventyGrass()
                + " aboveAir=" + scan.aboveSeventyAir());
        output.accept("LMW_MV_GEOLOGY_ANCHORS_PASS count=" + geologyAnchors.size()
                + " xPair=X_GRAVEL zPair=Z_GRANITE");
        output.accept("LMW_MV_ORE_ANCHORS_PASS count=" + oreAnchors.size()
                + " xPair=X_COAL zPair=Z_IRON");
        output.accept("LMW_MV_Y11_PASS counts=" + formatCounts(scan.yElevenCounts()));
        output.accept("LMW_MV_BOUNDARIES_PASS geology=X_GRAVEL,Z_GRANITE ore=X_COAL,Z_IRON");
        output.accept("LMW_MV_BIOME_PASS biome=PLAINS checks=" + scan.biomeChecks());
        output.accept("LMW_MV_SCAN_PASS range=-64..319 chunks=-1..0,-1..0"
                + " y5_67_counts=" + formatCounts(scan.yFiveToSixtySevenCounts())
                + " chunk_checksums=" + formatChunkChecksums(scan.chunkChecksums())
                + " y5_67_checksum=" + scan.yFiveToSixtySevenChecksum()
                + " forbidden=" + scan.forbiddenCount()
                + " full_counts=" + formatCounts(scan.fullCounts()));
        VerificationSummary summary = new VerificationSummary(
                world.getName(),
                uid,
                world.getSeed(),
                world.getEnvironment().name(),
                generator.getClass().getName(),
                world.getMinHeight(),
                world.getMaxHeight(),
                spawn.getX() + "," + spawn.getY() + "," + spawn.getZ(),
                fixedSpawn.getX() + "," + fixedSpawn.getY() + "," + fixedSpawn.getZ(),
                geologyAnchors.size(),
                oreAnchors.size(),
                formatCounts(scan.yElevenCounts()),
                formatCounts(scan.yFiveToSixtySevenCounts()),
                scan.yFiveToSixtySevenChecksum(),
                scan.forbiddenCount(),
                scan.biomeChecks());
        output.accept(summary.toMarker());
        return summary;
    }

    private static void validateScan(ScanResult scan) {
        require(scan.belowZeroAir() == 65_536, "below_zero_air_count");
        require(scan.yZeroBedrock() == 1_024, "y_zero_bedrock_count");
        require(scan.yOneToFourCounts().keySet().stream()
                .allMatch(SnapshotScanner.Y_ONE_TO_FOUR_ALLOWED::contains), "y_one_to_four_material");
        require(scan.yOneToFourCounts().values().stream().mapToInt(Integer::intValue).sum() == 4_096,
                "y_one_to_four_volume");
        require(scan.yOneToFourBedrock() > 0 && scan.yOneToFourNonBedrock() > 0,
                "y_one_to_four_mixture");
        require(scan.yFiveToSixtySevenCounts().equals(
                SnapshotScanner.EXPECTED_Y_FIVE_TO_SIXTY_SEVEN), "y_five_to_sixty_seven_counts");
        require(scan.chunkChecksums().equals(SnapshotScanner.EXPECTED_CHUNK_CHECKSUMS),
                "chunk_checksums");
        require(scan.yFiveToSixtySevenChecksum() == SnapshotScanner.EXPECTED_COMBINED_CHECKSUM,
                "combined_checksum");
        require(scan.ySixtyEightToSixtyNineDirt() == 2_048, "dirt_surface_count");
        require(scan.ySeventyGrass() == 1_024, "grass_surface_count");
        require(scan.aboveSeventyAir() == 254_976, "above_surface_air_count");
        require(scan.yElevenCounts().equals(SnapshotScanner.EXPECTED_Y_ELEVEN), "y_eleven_counts");
        require(scan.forbiddenCount() == 0, "forbidden_material");
        require(scan.unknownNonAir().isEmpty(), "unknown_non_air_" + scan.unknownNonAir());
        require(scan.biomeChecks() == 4_096, "biome_check_count");
        require(scan.fullCounts().values().stream().mapToInt(Integer::intValue).sum() == 393_216,
                "full_scan_volume");
    }

    private static void validateAnchorMetadata(
            List<Anchor> geologyAnchors, List<Anchor> oreAnchors) {
        require(geologyAnchors.size() == 10, "geology_anchor_count");
        for (Material material : List.of(
                Material.DIRT,
                Material.GRAVEL,
                Material.GRANITE,
                Material.DIORITE,
                Material.ANDESITE)) {
            require(countMaterial(geologyAnchors, material) == 2,
                    "geology_anchor_material_" + material);
        }
        require(countPair(geologyAnchors, "X_GRAVEL") == 2, "geology_x_pair");
        require(countPair(geologyAnchors, "Z_GRANITE") == 2, "geology_z_pair");

        require(oreAnchors.size() == 14, "ore_anchor_count");
        require(countMaterial(oreAnchors, Material.COAL_ORE) == 3, "coal_anchor_count");
        require(countMaterial(oreAnchors, Material.IRON_ORE) == 3, "iron_anchor_count");
        for (Material material : List.of(
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE,
                Material.LAPIS_ORE)) {
            require(countMaterial(oreAnchors, material) == 2,
                    "ore_anchor_material_" + material);
        }
        require(countPair(oreAnchors, "X_COAL") == 2, "ore_x_pair");
        require(countPair(oreAnchors, "Z_IRON") == 2, "ore_z_pair");
        for (Material material : List.of(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE)) {
            require(oreAnchors.stream().anyMatch(
                    anchor -> anchor.expectedMaterial() == material && anchor.y() == 11),
                    "y_eleven_anchor_" + material);
        }
    }

    private static long countMaterial(List<Anchor> anchors, Material material) {
        return anchors.stream().filter(anchor -> anchor.expectedMaterial() == material).count();
    }

    private static long countPair(List<Anchor> anchors, String pairId) {
        return anchors.stream().filter(anchor -> anchor.pairId().equals(pairId)).count();
    }

    static String formatCounts(Map<Material, Integer> counts) {
        Map<String, Integer> sorted = new TreeMap<>();
        counts.forEach((material, count) -> sorted.put(material.name(), count));
        return sorted.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private static String formatChunkChecksums(Map<ChunkKey, Long> checksums) {
        StringBuilder value = new StringBuilder();
        for (ChunkKey chunk : SnapshotScanner.CHUNKS) {
            if (!value.isEmpty()) {
                value.append(';');
            }
            value.append(chunk).append(':').append(checksums.get(chunk));
        }
        return value.toString();
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new VerificationException(reason);
        }
    }
}
