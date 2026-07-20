package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("geology-adapter")
class GeologySmokeAnchorsTest {
    private static final long FIXED_SEED = 1_165_2021L;
    private static final String RESOURCE = "/geology-smoke-anchors.tsv";

    @Test
    void freezesTenPureModelAnchorsAndTheirSourceMetadata() throws IOException {
        List<Anchor> anchors = loadAnchors();
        Set<Position> coordinates = new HashSet<>();
        Map<Material, Integer> materialCounts = new EnumMap<>(Material.class);
        Map<Position, FinalPlacement> finalPlacements = applyNeededChunks(anchors);

        assertEquals(10, anchors.size());
        for (Anchor anchor : anchors) {
            Position position = new Position(anchor.x(), anchor.y(), anchor.z());
            assertTrue(coordinates.add(position), () -> "duplicate anchor: " + anchor.id());
            assertEquals(Math.floorDiv(anchor.x(), 16), anchor.targetChunkX());
            assertEquals(Math.floorDiv(anchor.z(), 16), anchor.targetChunkZ());
            assertTrue(anchor.targetChunkX() >= -1 && anchor.targetChunkX() <= 0);
            assertTrue(anchor.targetChunkZ() >= -1 && anchor.targetChunkZ() <= 0);

            FinalPlacement actual = finalPlacements.get(position);
            assertNotNull(actual, () -> "missing final placement: " + anchor.id());
            assertEquals(anchor.expectedMaterial(), actual.material(), anchor.id());
            LegacyPlacement placement = actual.placement();
            assertEquals(anchor.sourceChunkX(), placement.sourceChunkX(), anchor.id());
            assertEquals(anchor.sourceChunkZ(), placement.sourceChunkZ(), anchor.id());
            assertEquals(anchor.feature().stableOrder(), placement.featureOrder(), anchor.id());
            assertEquals(anchor.attempt(), placement.attemptIndex(), anchor.id());
            assertEquals(anchor.veinSequence(), placement.veinSequence(), anchor.id());
            materialCounts.merge(anchor.expectedMaterial(), 1, Integer::sum);
        }

        for (Material material : List.of(
                Material.DIRT,
                Material.GRAVEL,
                Material.GRANITE,
                Material.DIORITE,
                Material.ANDESITE)) {
            assertEquals(2, materialCounts.getOrDefault(material, 0), material::name);
        }
        assertBoundaryPair(anchors, "X_GRAVEL", true);
        assertBoundaryPair(anchors, "Z_GRANITE", false);
    }

    private static void assertBoundaryPair(
            List<Anchor> anchors, String pairId, boolean xBoundary) {
        List<Anchor> pair = anchors.stream()
                .filter(anchor -> anchor.pairId().equals(pairId))
                .toList();
        assertEquals(2, pair.size());
        Anchor first = pair.get(0);
        Anchor second = pair.get(1);
        assertEquals(first.sourceChunkX(), second.sourceChunkX());
        assertEquals(first.sourceChunkZ(), second.sourceChunkZ());
        assertEquals(first.feature(), second.feature());
        assertEquals(first.attempt(), second.attempt());
        assertEquals(first.expectedMaterial(), second.expectedMaterial());
        if (xBoundary) {
            assertEquals(Set.of(-1, 0), Set.of(first.x(), second.x()));
            assertEquals(Math.floorDiv(first.z(), 16), Math.floorDiv(second.z(), 16));
        } else {
            assertEquals(Set.of(-1, 0), Set.of(first.z(), second.z()));
            assertEquals(Math.floorDiv(first.x(), 16), Math.floorDiv(second.x(), 16));
        }
    }

    private static Map<Position, FinalPlacement> applyNeededChunks(List<Anchor> anchors) {
        Set<ChunkPosition> chunks = new HashSet<>();
        anchors.forEach(anchor -> chunks.add(
                new ChunkPosition(anchor.targetChunkX(), anchor.targetChunkZ())));
        Map<Position, FinalPlacement> results = new HashMap<>();
        LegacyGeologyPlanner planner = new LegacyGeologyPlanner();
        for (ChunkPosition chunk : chunks) {
            InMemoryGeologyWorld world =
                    new InMemoryGeologyWorld(chunk.x(), chunk.z(), -64, 320);
            for (LegacyPlacement placement : planner.plan(FIXED_SEED, chunk.x(), chunk.z())) {
                if (!world.isAccessible(placement.x(), placement.y(), placement.z())) {
                    continue;
                }
                if (LegacyReplaceableBlock.isReplaceable(
                        world.getBlockKind(placement.x(), placement.y(), placement.z()))) {
                    world.setMaterial(
                            placement.x(), placement.y(), placement.z(), placement.material());
                    results.put(
                            new Position(placement.x(), placement.y(), placement.z()),
                            new FinalPlacement(
                                    placement,
                                    LegacyGeologyMaterialAdapter.toMaterial(placement.material())));
                }
            }
        }
        return results;
    }

    static List<Anchor> loadAnchors() throws IOException {
        var input = GeologySmokeAnchorsTest.class.getResourceAsStream(RESOURCE);
        assertNotNull(input, "anchor resource must exist");
        List<Anchor> anchors = new ArrayList<>();
        try (var reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            boolean headerSeen = false;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    assertEquals(
                            "id\tx\ty\tz\texpected_material\tpurpose\tpair_id\t"
                                    + "source_chunk_x\tsource_chunk_z\tfeature\tattempt\tvein_sequence",
                            line);
                    headerSeen = true;
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                assertEquals(12, columns.length, line);
                anchors.add(new Anchor(
                        columns[0],
                        Integer.parseInt(columns[1]),
                        Integer.parseInt(columns[2]),
                        Integer.parseInt(columns[3]),
                        Material.valueOf(columns[4]),
                        columns[5],
                        columns[6],
                        Integer.parseInt(columns[7]),
                        Integer.parseInt(columns[8]),
                        LegacyGeologyFeature.valueOf(columns[9]),
                        Integer.parseInt(columns[10]),
                        Integer.parseInt(columns[11])));
            }
            assertTrue(headerSeen);
        }
        assertFalse(anchors.isEmpty());
        return List.copyOf(anchors);
    }

    record Anchor(
            String id,
            int x,
            int y,
            int z,
            Material expectedMaterial,
            String purpose,
            String pairId,
            int sourceChunkX,
            int sourceChunkZ,
            LegacyGeologyFeature feature,
            int attempt,
            int veinSequence) {
        int targetChunkX() {
            return Math.floorDiv(x, 16);
        }

        int targetChunkZ() {
            return Math.floorDiv(z, 16);
        }
    }

    private record Position(int x, int y, int z) {
    }

    private record ChunkPosition(int x, int z) {
    }

    private record FinalPlacement(LegacyPlacement placement, Material material) {
    }
}
