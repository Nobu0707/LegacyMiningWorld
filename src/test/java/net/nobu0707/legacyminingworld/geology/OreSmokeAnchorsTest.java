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
import net.nobu0707.legacyminingworld.ore.LegacyOreFeature;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterial;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterialAdapter;
import net.nobu0707.legacyminingworld.ore.LegacyOrePlacement;
import net.nobu0707.legacyminingworld.ore.LegacyOrePlanner;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class OreSmokeAnchorsTest {
    private static final String RESOURCE = "/ore-smoke-anchors.tsv";

    @Test
    void freezesCombinedOreAnchorsMetadataYElevenBoundariesAndGeologyRegression()
            throws IOException {
        List<Anchor> anchors = loadAnchors();
        Map<UndergroundWorldModelTest.ChunkPosition, InMemoryGeologyWorld> worlds =
                combinedWorlds();
        Set<Position> coordinates = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Map<LegacyOreMaterial, Integer> materialCounts = new EnumMap<>(LegacyOreMaterial.class);
        LegacyOrePlanner planner = new LegacyOrePlanner();

        assertEquals(14, anchors.size());
        for (Anchor anchor : anchors) {
            assertTrue(ids.add(anchor.id()), () -> "duplicate anchor id: " + anchor.id());
            Position position = new Position(anchor.x(), anchor.y(), anchor.z());
            assertTrue(coordinates.add(position), () -> "duplicate anchor: " + anchor.id());
            assertTrue(anchor.y() >= 5 && anchor.y() < 68, anchor::id);
            assertTrue(anchor.targetChunkX() >= -1 && anchor.targetChunkX() <= 0, anchor::id);
            assertTrue(anchor.targetChunkZ() >= -1 && anchor.targetChunkZ() <= 0, anchor::id);
            InMemoryGeologyWorld world = worlds.get(new UndergroundWorldModelTest.ChunkPosition(
                    anchor.targetChunkX(), anchor.targetChunkZ()));
            assertNotNull(world, anchor::id);
            assertEquals(
                    LegacyOreMaterialAdapter.toMaterial(anchor.expectedMaterial()),
                    world.getMaterial(anchor.x(), anchor.y(), anchor.z()),
                    anchor.id());

            List<LegacyOrePlacement> placements = planner.plan(
                    UndergroundWorldModelTest.FIXED_SEED,
                    anchor.targetChunkX(),
                    anchor.targetChunkZ());
            assertTrue(placements.stream().anyMatch(placement -> anchor.matches(placement)),
                    () -> "planner metadata missing: " + anchor.id());
            materialCounts.merge(anchor.expectedMaterial(), 1, Integer::sum);
        }

        assertEquals(Map.of(
                LegacyOreMaterial.COAL_ORE, 3,
                LegacyOreMaterial.IRON_ORE, 3,
                LegacyOreMaterial.GOLD_ORE, 2,
                LegacyOreMaterial.REDSTONE_ORE, 2,
                LegacyOreMaterial.DIAMOND_ORE, 2,
                LegacyOreMaterial.LAPIS_ORE, 2), materialCounts);
        for (LegacyOreMaterial material : List.of(
                LegacyOreMaterial.COAL_ORE,
                LegacyOreMaterial.IRON_ORE,
                LegacyOreMaterial.GOLD_ORE,
                LegacyOreMaterial.REDSTONE_ORE,
                LegacyOreMaterial.DIAMOND_ORE)) {
            assertTrue(anchors.stream().anyMatch(anchor ->
                    anchor.expectedMaterial() == material && anchor.y() == 11), material::name);
        }
        assertBoundaryPair(anchors, "X_COAL", true);
        assertBoundaryPair(anchors, "Z_IRON", false);

        for (GeologySmokeAnchorsTest.Anchor anchor : GeologySmokeAnchorsTest.loadAnchors()) {
            InMemoryGeologyWorld world = worlds.get(new UndergroundWorldModelTest.ChunkPosition(
                    anchor.targetChunkX(), anchor.targetChunkZ()));
            assertEquals(anchor.expectedMaterial(),
                    world.getMaterial(anchor.x(), anchor.y(), anchor.z()), anchor.id());
        }
        System.out.printf(
                "ORE_ANCHOR_TOTAL seed=%d count=%d materials=%s xPair=X_COAL PASS zPair=Z_IRON PASS geology=10/10 PASS%n",
                UndergroundWorldModelTest.FIXED_SEED, anchors.size(), materialCounts);
    }

    static List<Anchor> loadAnchors() throws IOException {
        var input = OreSmokeAnchorsTest.class.getResourceAsStream(RESOURCE);
        assertNotNull(input, "ore anchor resource must exist");
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
                        LegacyOreMaterial.valueOf(columns[4]),
                        columns[5],
                        columns[6],
                        Integer.parseInt(columns[7]),
                        Integer.parseInt(columns[8]),
                        LegacyOreFeature.valueOf(columns[9]),
                        Integer.parseInt(columns[10]),
                        Integer.parseInt(columns[11])));
            }
            assertTrue(headerSeen);
        }
        assertFalse(anchors.isEmpty());
        return List.copyOf(anchors);
    }

    private static Map<UndergroundWorldModelTest.ChunkPosition, InMemoryGeologyWorld>
            combinedWorlds() {
        Map<UndergroundWorldModelTest.ChunkPosition, InMemoryGeologyWorld> worlds =
                new HashMap<>();
        for (UndergroundWorldModelTest.ChunkPosition chunk : UndergroundWorldModelTest.CHUNKS) {
            InMemoryGeologyWorld world = new InMemoryGeologyWorld(
                    chunk.x(),
                    chunk.z(),
                    UndergroundWorldModelTest.MINIMUM_Y,
                    UndergroundWorldModelTest.MAXIMUM_Y_EXCLUSIVE);
            LegacyUndergroundPopulator.applyUnderground(
                    UndergroundWorldModelTest.FIXED_SEED,
                    chunk.x(),
                    chunk.z(),
                    UndergroundWorldModelTest.MINIMUM_Y,
                    UndergroundWorldModelTest.MAXIMUM_Y_EXCLUSIVE,
                    world);
            worlds.put(chunk, world);
        }
        return Map.copyOf(worlds);
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
        if (xBoundary) {
            assertEquals(Set.of(-1, 0), Set.of(first.x(), second.x()));
            assertEquals(Set.of(-1, 0), Set.of(first.targetChunkX(), second.targetChunkX()));
        } else {
            assertEquals(Set.of(-1, 0), Set.of(first.z(), second.z()));
            assertEquals(Set.of(-1, 0), Set.of(first.targetChunkZ(), second.targetChunkZ()));
        }
    }

    record Anchor(
            String id,
            int x,
            int y,
            int z,
            LegacyOreMaterial expectedMaterial,
            String purpose,
            String pairId,
            int sourceChunkX,
            int sourceChunkZ,
            LegacyOreFeature feature,
            int attempt,
            int veinSequence) {
        int targetChunkX() {
            return Math.floorDiv(x, 16);
        }

        int targetChunkZ() {
            return Math.floorDiv(z, 16);
        }

        boolean matches(LegacyOrePlacement placement) {
            return placement.x() == x
                    && placement.y() == y
                    && placement.z() == z
                    && placement.material() == expectedMaterial
                    && placement.sourceChunkX() == sourceChunkX
                    && placement.sourceChunkZ() == sourceChunkZ
                    && placement.featureOrder() == feature.stableOrder()
                    && placement.attemptIndex() == attempt
                    && placement.veinSequence() == veinSequence;
        }
    }

    private record Position(int x, int y, int z) {
    }
}
