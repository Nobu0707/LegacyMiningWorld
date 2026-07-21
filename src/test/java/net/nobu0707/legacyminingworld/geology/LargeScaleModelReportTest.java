package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("large-scale-model")
class LargeScaleModelReportTest {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final List<Material> MATERIALS = List.of(
            Material.STONE, Material.DIRT, Material.GRAVEL, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.COAL_ORE, Material.IRON_ORE,
            Material.GOLD_ORE, Material.REDSTONE_ORE, Material.DIAMOND_ORE,
            Material.LAPIS_ORE);
    private static final List<Material> ORES = MATERIALS.subList(6, 12);
    private static final String CHUNK_HEADER =
            "chunkX\tchunkZ\ty5_67Checksum\tfullChecksum\tSTONE\tDIRT\tGRAVEL\t"
            + "GRANITE\tDIORITE\tANDESITE\tCOAL_ORE\tIRON_ORE\tGOLD_ORE\t"
            + "REDSTONE_ORE\tDIAMOND_ORE\tLAPIS_ORE";

    @Test
    void writesCanonicalOneThousandEightyNineChunkExpectedReports() throws Exception {
        Spec spec = loadSpec();
        assertEquals(1_089, spec.chunkCount());
        assertEquals(17_563_392L, (long) spec.chunkCount() * 63 * 256);
        Path reportDirectory = reportDirectory();
        Files.createDirectories(reportDirectory);

        StringBuilder chunks = new StringBuilder(CHUNK_HEADER).append('\n');
        Map<Material, Long> totals = new EnumMap<>(Material.class);
        Map<Material, Long> yEleven = new EnumMap<>(Material.class);
        Map<Material, long[]> histogram = new EnumMap<>(Material.class);
        for (Material ore : ORES) histogram.put(ore, new long[68]);
        long combined = FNV_OFFSET;
        int produced = 0;
        long started = System.nanoTime();

        for (int chunkZ = spec.minimumChunkZ(); chunkZ <= spec.maximumChunkZ(); chunkZ++) {
            for (int chunkX = spec.minimumChunkX(); chunkX <= spec.maximumChunkX(); chunkX++) {
                InMemoryGeologyWorld world = new InMemoryGeologyWorld(
                        chunkX, chunkZ, spec.minimumY(), spec.maximumYExclusive());
                LegacyUndergroundPopulator.applyUnderground(
                        spec.seed(), chunkX, chunkZ,
                        spec.minimumY(), spec.maximumYExclusive(), world);
                Map<Material, Long> chunkCounts = new EnumMap<>(Material.class);
                long chunkChecksum = FNV_OFFSET;
                int minimumX = chunkX * 16;
                int minimumZ = chunkZ * 16;
                for (int y = 0; y <= 67; y++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            Material material = world.getMaterial(
                                    minimumX + localX, y, minimumZ + localZ);
                            if (y == 11) yEleven.merge(material, 1L, Long::sum);
                            if (y >= 5 && histogram.containsKey(material)) {
                                histogram.get(material)[y]++;
                            }
                            if (y >= 5) {
                                chunkCounts.merge(material, 1L, Long::sum);
                                totals.merge(material, 1L, Long::sum);
                                chunkChecksum = mixBlock(chunkChecksum,
                                        minimumX + localX, y, minimumZ + localZ, material);
                                combined = mixBlock(combined,
                                        minimumX + localX, y, minimumZ + localZ, material);
                            }
                        }
                    }
                }
                chunks.append(chunkX).append('\t').append(chunkZ).append('\t')
                        .append(chunkChecksum).append("\t0");
                for (Material material : MATERIALS) {
                    chunks.append('\t').append(chunkCounts.getOrDefault(material, 0L));
                }
                chunks.append('\n');
                produced++;
            }
        }

        assertEquals(spec.chunkCount(), produced);
        assertEquals(17_563_392L, totals.values().stream().mapToLong(Long::longValue).sum());
        for (Material material : MATERIALS) {
            assertTrue(totals.getOrDefault(material, 0L) > 0, material::name);
        }
        for (Material ore : ORES.subList(0, 5)) {
            assertTrue(yEleven.getOrDefault(ore, 0L) > 0, ore::name);
        }

        String summary = "worldName=" + spec.worldName() + "\n"
                + "seed=" + spec.seed() + "\n"
                + "chunkRange=" + spec.minimumChunkX() + ".." + spec.maximumChunkX()
                + "," + spec.minimumChunkZ() + ".." + spec.maximumChunkZ() + "\n"
                + "chunkCount=" + spec.chunkCount() + "\n"
                + "y5_67Blocks=17563392\n"
                + "y5_67Checksum=" + combined + "\n"
                + "y5_67MaterialCounts=" + formatCounts(totals) + "\n"
                + "y11MaterialCounts=" + formatCounts(yEleven) + "\n"
                + "PASS=true\n";
        String histogramText = histogram(histogram);
        Path chunksPath = reportDirectory.resolve("expected-chunks.txt");
        Path summaryPath = reportDirectory.resolve("expected-summary.txt");
        Path histogramPath = reportDirectory.resolve("expected-ore-height-histogram.txt");
        Files.writeString(chunksPath, chunks, StandardCharsets.UTF_8);
        Files.writeString(summaryPath, summary, StandardCharsets.UTF_8);
        Files.writeString(histogramPath, histogramText, StandardCharsets.UTF_8);
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(Locale.ROOT,
                "LARGE_SCALE_MODEL_PASS version=%s chunks=%d blocks=17563392 elapsed=%.3f "
                + "checksum=%d histogramChecksum=%s chunksSha256=%s summarySha256=%s histogramSha256=%s counts=%s%n",
                System.getProperty("legacyminingworld.version"), produced, elapsed, combined,
                sha256(histogramText.getBytes(StandardCharsets.UTF_8)), sha256(chunksPath),
                sha256(summaryPath), sha256(histogramPath), formatCounts(totals));
    }

    private static Spec loadSpec() throws IOException {
        Properties values = new Properties();
        try (InputStream input = LargeScaleModelReportTest.class.getResourceAsStream(
                "/large-scale-grid.properties")) {
            if (input == null) throw new IOException("missing large-scale-grid.properties");
            values.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return new Spec(
                values.getProperty("worldName"),
                Long.parseLong(values.getProperty("seed")),
                Integer.parseInt(values.getProperty("minimumChunkX")),
                Integer.parseInt(values.getProperty("maximumChunkX")),
                Integer.parseInt(values.getProperty("minimumChunkZ")),
                Integer.parseInt(values.getProperty("maximumChunkZ")),
                Integer.parseInt(values.getProperty("chunkCount")),
                Integer.parseInt(values.getProperty("minimumY")),
                Integer.parseInt(values.getProperty("maximumYExclusive")));
    }

    private static Path reportDirectory() {
        String configured = System.getProperty("legacyminingworld.largeScaleReportDir");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("large-scale report directory is not configured");
        }
        Path project = Path.of("").toAbsolutePath().normalize();
        Path report = Path.of(configured).toAbsolutePath().normalize();
        if (!report.startsWith(project.resolve("build"))) {
            throw new IllegalArgumentException("reports must remain below build/");
        }
        return report;
    }

    private static String histogram(Map<Material, long[]> values) {
        StringBuilder output = new StringBuilder(
                "Y\tCOAL_ORE\tIRON_ORE\tGOLD_ORE\tREDSTONE_ORE\tDIAMOND_ORE\tLAPIS_ORE\n");
        for (int y = 0; y <= 67; y++) {
            output.append(y);
            for (Material ore : ORES) output.append('\t').append(values.get(ore)[y]);
            output.append('\n');
        }
        return output.toString();
    }

    private static String formatCounts(Map<Material, Long> values) {
        Map<String, Long> sorted = new TreeMap<>();
        values.forEach((material, count) -> sorted.put(material.name(), count));
        return sorted.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right).orElse("none");
    }

    private static long mixBlock(long hash, int x, int y, int z, Material material) {
        long mixed = mix(hash, x);
        mixed = mix(mixed, y);
        mixed = mix(mixed, z);
        for (char character : material.name().toCharArray()) mixed = mix(mixed, character);
        return mixed;
    }

    private static long mix(long hash, int value) {
        return (hash ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
    }

    private static String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Spec(String worldName, long seed, int minimumChunkX, int maximumChunkX,
            int minimumChunkZ, int maximumChunkZ, int chunkCount, int minimumY,
            int maximumYExclusive) {
    }
}
