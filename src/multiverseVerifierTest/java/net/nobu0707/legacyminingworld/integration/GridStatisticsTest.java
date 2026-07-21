package net.nobu0707.legacyminingworld.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("large-scale-verifier")
class GridStatisticsTest {
    @Test
    void calculatesStablePopulationStatisticsAndLocaleRootFormatting() {
        GridStatistics statistics = GridStatistics.calculate(new long[] {0, 1, 2, 3, 4});
        assertEquals(10, statistics.total());
        assertEquals(2.0, statistics.mean());
        assertEquals(Math.sqrt(2.0), statistics.populationStandardDeviation(), 0.000_000_1);
        assertEquals(0, statistics.minimum());
        assertEquals(4, statistics.maximum());
        assertEquals(2.0, statistics.median());
        assertEquals(4, statistics.p95());
        assertEquals(1, statistics.zeroCount());
        assertEquals("total=10 mean=2.000 populationStdDev=1.414 min=0 max=4 "
                + "median=2.000 p95=4 zeroChunks=1", statistics.format());
    }

    @Test
    void freezesReportHeadersAndEmbeddedGridResource() throws Exception {
        assertEquals("chunkX\tchunkZ\ty5_67Checksum\tfullChecksum\tSTONE\tDIRT\tGRAVEL\t"
                + "GRANITE\tDIORITE\tANDESITE\tCOAL_ORE\tIRON_ORE\tGOLD_ORE\t"
                + "REDSTONE_ORE\tDIAMOND_ORE\tLAPIS_ORE", GridReportWriter.CHUNK_HEADER);
        try (var input = getClass().getResourceAsStream("/large-scale-grid.properties")) {
            assertTrue(input != null);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("chunkCount=1089"));
            assertTrue(text.contains("maximumYExclusive=320"));
        }
    }
}
