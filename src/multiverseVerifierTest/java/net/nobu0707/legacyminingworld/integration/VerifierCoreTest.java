package net.nobu0707.legacyminingworld.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class VerifierCoreTest {
    @Test
    void freezesAllowedForbiddenGoldenCountsAndChecksumOrder() {
        assertTrue(SnapshotScanner.ALLOWED_NON_AIR.contains(Material.GRASS_BLOCK));
        assertFalse(SnapshotScanner.Y_ONE_TO_FOUR_ALLOWED.contains(Material.GRASS_BLOCK));
        assertTrue(SnapshotScanner.FORBIDDEN.contains(Material.WATER));
        assertTrue(SnapshotScanner.FORBIDDEN.contains(Material.DEEPSLATE_DIAMOND_ORE));
        assertEquals(25, SnapshotScanner.FORBIDDEN.size());
        assertEquals(63 * 256 * 4, SnapshotScanner.EXPECTED_Y_FIVE_TO_SIXTY_SEVEN
                .values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(-7_305_870_198_059_528_782L,
                SnapshotScanner.EXPECTED_COMBINED_CHECKSUM);

        long first = ScanChecksum.mixBlock(
                ScanChecksum.OFFSET_BASIS, -16, 5, -16, Material.STONE);
        long second = ScanChecksum.mixBlock(first, -15, 5, -16, Material.GRANITE);
        long reversed = ScanChecksum.mixBlock(
                ScanChecksum.OFFSET_BASIS, -15, 5, -16, Material.GRANITE);
        reversed = ScanChecksum.mixBlock(reversed, -16, 5, -16, Material.STONE);
        assertEquals(5_861_471_778_942_622_843L, second);
        assertFalse(second == reversed);
    }

    @Test
    void parsesSeedsAndStableMarkersForRestartComparison() {
        assertEquals(1_165_2021L, VerifierSupport.parseExpectedSeed("11652021"));
        assertThrows(IllegalArgumentException.class,
                () -> VerifierSupport.parseExpectedSeed("not-a-seed"));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("uid", "1234");
        values.put("checksum", -99);
        String marker = VerifierSupport.marker("LMW_MV_VERIFY_PASS", values);
        assertEquals("LMW_MV_VERIFY_PASS uid=1234 checksum=-99", marker);
        assertEquals(Map.of("uid", "1234", "checksum", "-99"),
                VerifierSupport.parseMarker(marker, "LMW_MV_VERIFY_PASS"));
        assertThrows(IllegalArgumentException.class,
                () -> VerifierSupport.parseMarker("LMW_MV_VERIFY_FAIL reason=x",
                        "LMW_MV_VERIFY_PASS"));
    }

    @Test
    void formatsWorldSummaryAsOneStablePassMarker() {
        VerificationSummary summary = new VerificationSummary(
                "legacy_mining_mv_smoke",
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                1_165_2021L,
                "NORMAL",
                "generator.Class",
                -64,
                320,
                "0.0,71.0,0.0",
                "0.5,71.0,0.5",
                10,
                14,
                "COAL_ORE:6",
                "STONE:51999",
                -7_305_870_198_059_528_782L,
                0,
                4096);
        Map<String, String> parsed = VerifierSupport.parseMarker(
                summary.toMarker(), "LMW_MV_VERIFY_PASS");
        assertEquals("legacy_mining_mv_smoke", parsed.get("world"));
        assertEquals("10", parsed.get("geology"));
        assertEquals("14", parsed.get("ore"));
        assertEquals("0", parsed.get("forbidden"));
    }

    @Test
    void processedTestPluginMetadataIsExplicitAndProductionClassesAreAbsent()
            throws IOException {
        String descriptor;
        try (var input = getClass().getResourceAsStream("/plugin.yml")) {
            assertTrue(input != null);
            descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(descriptor.contains("name: LegacyMiningWorldMultiverseVerifier"));
        assertTrue(descriptor.contains("version: "
                + System.getProperty("legacyminingworld.version")));
        assertTrue(descriptor.contains("description: Test-only Phase 4A/4B1/4B2 verifier"));
        assertTrue(descriptor.contains("  - LegacyMiningWorld"));
        assertTrue(descriptor.contains("  - Multiverse-Core"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin"));
    }
}
