package net.nobu0707.legacyminingworld.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("large-scale-verifier")
class LargeScaleGridSpecTest {
    private static final String VALID = """
            worldName=legacy_mining_scale
            seed=11652021
            minimumChunkX=-16
            maximumChunkX=16
            minimumChunkZ=-16
            maximumChunkZ=16
            chunkCount=1089
            minimumY=-64
            maximumYExclusive=320
            """;

    @Test
    void parsesRequiredSpecAndFreezesDerivedVolumes() throws IOException {
        LargeScaleGridSpec spec = parse(VALID);
        assertEquals("legacy_mining_scale", spec.worldName());
        assertEquals(1_089, spec.chunkCount());
        assertEquals(107_053_056L, spec.blockCount());
        assertEquals(spec, LargeScaleGridSpec.loadRequired(getClass()));
    }

    @Test
    void rejectsMissingUnknownDuplicateInvalidNumbersRangesCountsAndNames() {
        assertThrows(IOException.class, () -> parse(VALID.replace("seed=11652021\n", "")));
        assertThrows(IOException.class, () -> parse(VALID + "other=1\n"));
        assertThrows(IOException.class, () -> parse(VALID + "seed=2\n"));
        assertThrows(IOException.class, () -> parse(VALID.replace("seed=11652021", "seed=no")));
        assertThrows(IOException.class, () -> parse(VALID.replace(
                "minimumChunkX=-16", "minimumChunkX=17")));
        assertThrows(IOException.class, () -> parse(VALID.replace("chunkCount=1089", "chunkCount=1")));
        for (String unsafe : new String[] {"../world", "world/name", "world\\name", "World"}) {
            assertThrows(IOException.class, () -> parse(VALID.replace(
                    "worldName=legacy_mining_scale", "worldName=" + unsafe)));
        }
    }

    @Test
    void forwardAndReverseAreExactOppositeOrdersWithSameUniqueSet() throws IOException {
        LargeScaleGridSpec spec = parse(VALID);
        var forward = spec.chunks(GridOrder.FORWARD);
        var reverse = spec.chunks(GridOrder.REVERSE);
        assertEquals(1_089, forward.size());
        assertEquals(new ChunkKey(-16, -16), forward.getFirst());
        assertEquals(new ChunkKey(16, 16), forward.getLast());
        assertEquals(forward.reversed(), reverse);
        assertEquals(new HashSet<>(forward), new HashSet<>(reverse));
        assertEquals(1_089, new HashSet<>(forward).size());
    }

    @Test
    void validatesModesOrdersAndReportIdentifiers() {
        assertEquals(GridMode.GENERATE, GridMode.parse("generate"));
        assertEquals(GridMode.EXISTING, GridMode.parse("existing"));
        assertEquals(GridOrder.FORWARD, GridOrder.parse("forward"));
        assertEquals(GridOrder.REVERSE, GridOrder.parse("reverse"));
        assertThrows(VerificationException.class, () -> GridMode.parse("other"));
        assertThrows(VerificationException.class, () -> GridOrder.parse("other"));
        assertTrue(GridReportWriter.isSafeReportId("a1"));
        assertTrue(GridReportWriter.isSafeReportId("run_01"));
        for (String unsafe : new String[] {"../a", "a/b", "a\\b", "/tmp/a", "A1", ""}) {
            assertTrue(!GridReportWriter.isSafeReportId(unsafe), unsafe);
        }
    }

    private static LargeScaleGridSpec parse(String text) throws IOException {
        return LargeScaleGridSpec.parse(new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)));
    }
}
