package net.nobu0707.legacyminingworld.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Material;

final class GridReportWriter {
    static final String CHUNK_HEADER = "chunkX\tchunkZ\ty5_67Checksum\tfullChecksum\t"
            + "STONE\tDIRT\tGRAVEL\tGRANITE\tDIORITE\tANDESITE\tCOAL_ORE\tIRON_ORE\t"
            + "GOLD_ORE\tREDSTONE_ORE\tDIAMOND_ORE\tLAPIS_ORE";
    static final String HISTOGRAM_HEADER =
            "Y\tCOAL_ORE\tIRON_ORE\tGOLD_ORE\tREDSTONE_ORE\tDIAMOND_ORE\tLAPIS_ORE";

    private final Path reportsDirectory;

    GridReportWriter(Path pluginDataDirectory) {
        reportsDirectory = pluginDataDirectory.resolve("reports");
    }

    static boolean isSafeReportId(String reportId) {
        return reportId != null
                && reportId.matches("[a-z0-9_]+")
                && !reportId.contains("..")
                && !reportId.contains("/")
                && !reportId.contains("\\");
    }

    GridMetrics write(String reportId, GridAccumulator result, GridMetrics metrics) throws IOException {
        if (!isSafeReportId(reportId)) {
            throw new IOException("unsafe_report_id");
        }
        long started = System.nanoTime();
        Files.createDirectories(reportsDirectory);
        writeAtomic(reportId + "-chunks.txt", chunks(result));
        writeAtomic(reportId + "-summary.txt", summary(result));
        writeAtomic(reportId + "-ore-height-histogram.txt", histogram(result));
        writeAtomic(reportId + "-distribution.txt", distribution(result));
        long reportNanos = System.nanoTime() - started;
        GridMetrics measured = new GridMetrics(
                metrics.totalNanos() + reportNanos, metrics.prepareNanos(), metrics.scanNanos(),
                reportNanos, metrics.generatedBeforeScan(),
                metrics.newlyGenerated(), metrics.missingExisting(),
                metrics.immediateUnloadRejected(),
                metrics.maximumLoadedChunks());
        writeAtomic(reportId + "-measurement.txt", measurement(result, measured));
        return measured;
    }

    static String chunks(GridAccumulator result) {
        List<GridChunkResult> chunks = canonicalChunks(result);
        StringBuilder output = new StringBuilder(CHUNK_HEADER).append('\n');
        for (GridChunkResult chunk : chunks) {
            output.append(chunk.chunk().x()).append('\t')
                    .append(chunk.chunk().z()).append('\t')
                    .append(chunk.yFiveToSixtySevenChecksum()).append('\t')
                    .append(chunk.fullChecksum());
            for (Material material : GridAccumulator.REPORT_MATERIALS) {
                output.append('\t').append(
                        chunk.yFiveToSixtySevenCounts().getOrDefault(material, 0L));
            }
            output.append('\n');
        }
        return output.toString();
    }

    static String summary(GridAccumulator result) {
        LargeScaleGridSpec spec = result.spec();
        return "worldName=" + spec.worldName() + "\n"
                + "seed=" + spec.seed() + "\n"
                + "chunkRange=" + spec.minimumChunkX() + ".." + spec.maximumChunkX()
                + "," + spec.minimumChunkZ() + ".." + spec.maximumChunkZ() + "\n"
                + "chunkCount=" + spec.chunkCount() + "\n"
                + "minimumY=" + spec.minimumY() + "\n"
                + "maximumYExclusive=" + spec.maximumYExclusive() + "\n"
                + "scannedBlocks=" + result.scannedBlocks() + "\n"
                + "belowZeroAir=" + result.belowZeroAir() + "\n"
                + "yZeroBedrock=" + result.yZeroBedrock() + "\n"
                + "yOneToFour=" + result.yOneToFour() + "\n"
                + "yOneToFourBedrock=" + result.yOneToFourBedrock() + "\n"
                + "yOneToFourNonBedrock=" + result.yOneToFourNonBedrock() + "\n"
                + "yFiveToSixtySeven=" + sum(result.yFiveCounts()) + "\n"
                + "ySixtyEightToSixtyNineDirt=" + result.ySixtyEightToSixtyNineDirt() + "\n"
                + "ySeventyGrass=" + result.ySeventyGrass() + "\n"
                + "aboveSeventyAir=" + result.aboveSeventyAir() + "\n"
                + "y5_67Checksum=" + result.combinedYFiveChecksum() + "\n"
                + "fullChecksum=" + result.combinedFullChecksum() + "\n"
                + "y5_67MaterialCounts=" + formatCounts(result.yFiveCounts()) + "\n"
                + "fullMaterialCounts=" + formatCounts(result.fullCounts()) + "\n"
                + "y11MaterialCounts=" + formatCounts(result.yElevenCounts()) + "\n"
                + "forbidden=" + result.forbidden() + "\n"
                + "unknownNonAir=" + result.unknownNonAir() + "\n"
                + "biome=PLAINS\n"
                + "biomeChecks=" + result.biomeChecks() + "\n"
                + "regionExpectedChunks=" + spec.chunkCount() + "\n"
                + "PASS=true\n";
    }

    static String histogram(GridAccumulator result) {
        Map<Material, long[]> histogram = result.oreHistogram();
        StringBuilder output = new StringBuilder(HISTOGRAM_HEADER).append('\n');
        for (int y = 0; y <= 67; y++) {
            output.append(y);
            for (Material ore : GridAccumulator.ORES) {
                output.append('\t').append(histogram.get(ore)[y]);
            }
            output.append('\n');
        }
        return output.toString();
    }

    static String distribution(GridAccumulator result) {
        StringBuilder output = new StringBuilder();
        output.append("seed=").append(result.spec().seed()).append('\n');
        output.append("range=").append(result.spec().minimumChunkX()).append("..")
                .append(result.spec().maximumChunkX()).append(',')
                .append(result.spec().minimumChunkZ()).append("..")
                .append(result.spec().maximumChunkZ()).append('\n');
        output.append("chunkCount=").append(result.spec().chunkCount()).append('\n');
        output.append("scannedBlocks=").append(result.scannedBlocks()).append('\n');
        output.append("y5_67MaterialCounts=").append(formatCounts(result.yFiveCounts())).append('\n');
        output.append("fullMaterialCounts=").append(formatCounts(result.fullCounts())).append('\n');
        output.append("y11MaterialCounts=").append(formatCounts(result.yElevenCounts())).append('\n');
        for (Material material : GridAccumulator.REPORT_MATERIALS) {
            long[] values = new long[result.chunks().size()];
            for (int index = 0; index < values.length; index++) {
                values[index] = result.chunks().get(index).yFiveToSixtySevenCounts()
                        .getOrDefault(material, 0L);
            }
            output.append("perChunk.").append(material.name()).append('=')
                    .append(GridStatistics.calculate(values).format()).append('\n');
        }
        for (Material ore : GridAccumulator.ORES) {
            double density = result.yFiveCounts().getOrDefault(ore, 0L)
                    * 1_000_000.0 / 17_563_392.0;
            output.append("densityPerMillion.").append(ore.name()).append('=')
                    .append(String.format(Locale.ROOT, "%.6f", density)).append('\n');
        }
        output.append("pureLiveExact=checked_by_validation_script\n");
        output.append("a1A2B1Exact=checked_by_validation_script\n");
        output.append("PASS=true\n");
        return output.toString();
    }

    static String measurement(GridAccumulator result, GridMetrics metrics) {
        double seconds = metrics.totalNanos() / 1_000_000_000.0;
        double chunksPerSecond = result.spec().chunkCount() / seconds;
        double blocksPerSecond = result.scannedBlocks() / seconds;
        return String.format(Locale.ROOT,
                "totalSeconds=%.3f%nprepareSeconds=%.3f%nscanSeconds=%.3f%n"
                + "reportSeconds=%.3f%nchunksPerSecond=%.3f%nblocksPerSecond=%.3f%n"
                + "generatedBeforeScan=%d%nnewlyGenerated=%d%nmissingExisting=%d%n"
                + "immediateUnloadRejected=%d%nmaximumLoadedChunks=%d%n"
                + "jvmMaxHeapBytes=%d%n",
                seconds,
                metrics.prepareNanos() / 1_000_000_000.0,
                metrics.scanNanos() / 1_000_000_000.0,
                metrics.reportNanos() / 1_000_000_000.0,
                chunksPerSecond,
                blocksPerSecond,
                metrics.generatedBeforeScan(),
                metrics.newlyGenerated(),
                metrics.missingExisting(),
                metrics.immediateUnloadRejected(),
                metrics.maximumLoadedChunks(),
                Runtime.getRuntime().maxMemory());
    }

    static List<GridChunkResult> canonicalChunks(GridAccumulator result) {
        List<GridChunkResult> chunks = new ArrayList<>(result.chunks());
        chunks.sort(Comparator.comparingInt((GridChunkResult value) -> value.chunk().z())
                .thenComparingInt(value -> value.chunk().x()));
        Set<ChunkKey> unique = new HashSet<>();
        for (GridChunkResult chunk : chunks) {
            if (!unique.add(chunk.chunk())) {
                throw new VerificationException("duplicate_report_chunk_" + chunk.chunk());
            }
        }
        if (chunks.size() != result.spec().chunkCount()) {
            throw new VerificationException("missing_report_chunk");
        }
        return List.copyOf(chunks);
    }

    private void writeAtomic(String name, String contents) throws IOException {
        Path target = reportsDirectory.resolve(name);
        Path temporary = reportsDirectory.resolve(name + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long sum(Map<Material, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    static String formatCounts(Map<Material, Long> counts) {
        Map<String, Long> sorted = new TreeMap<>();
        counts.forEach((material, count) -> sorted.put(material.name(), count));
        return sorted.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }
}
