package net.nobu0707.legacyminingworld.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class AnchorTableTest {
    private static final String HEADER = "id\tx\ty\tz\texpected_material\tpurpose\tpair_id\t"
            + "source_chunk_x\tsource_chunk_z\tfeature\tattempt\tvein_sequence\n";

    @Test
    void parsesAndFreezesBothRequiredAnchorTables() throws IOException {
        List<Anchor> geology = AnchorTable.loadRequiredResource(
                getClass(), "/geology-smoke-anchors.tsv");
        List<Anchor> ores = AnchorTable.loadRequiredResource(
                getClass(), "/ore-smoke-anchors.tsv");

        assertEquals(10, geology.size());
        assertEquals(14, ores.size());
        assertEquals(Map.of(
                Material.DIRT, 2L,
                Material.GRAVEL, 2L,
                Material.GRANITE, 2L,
                Material.DIORITE, 2L,
                Material.ANDESITE, 2L), counts(geology));
        assertEquals(2, geology.stream().filter(a -> a.pairId().equals("X_GRAVEL")).count());
        assertEquals(2, geology.stream().filter(a -> a.pairId().equals("Z_GRANITE")).count());
        assertEquals(2, ores.stream().filter(a -> a.pairId().equals("X_COAL")).count());
        assertEquals(2, ores.stream().filter(a -> a.pairId().equals("Z_IRON")).count());
        for (Material material : List.of(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE)) {
            assertTrue(ores.stream().anyMatch(
                    anchor -> anchor.expectedMaterial() == material && anchor.y() == 11));
        }
    }

    @Test
    void rejectsDuplicateIdsInvalidMaterialsAndMissingResources() {
        String row = "A\t0\t11\t0\tCOAL_ORE\ty11\t-\t0\t0\tCOAL\t0\t0\n";
        assertThrows(IOException.class, () -> parse(HEADER + row + row));
        assertThrows(IOException.class, () -> parse(HEADER
                + "A\t0\t11\t0\tNOT_A_MATERIAL\ty11\t-\t0\t0\tCOAL\t0\t0\n"));
        assertThrows(IOException.class, () -> AnchorTable.loadRequiredResource(
                getClass(), "/missing-anchor-table.tsv"));
    }

    private static List<Anchor> parse(String contents) throws IOException {
        return AnchorTable.parse(new ByteArrayInputStream(
                contents.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<Material, Long> counts(List<Anchor> anchors) {
        return anchors.stream().collect(Collectors.groupingBy(
                Anchor::expectedMaterial, Collectors.counting()));
    }
}
