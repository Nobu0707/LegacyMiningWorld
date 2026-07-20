package net.nobu0707.legacyminingworld.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class LegacyOreMaterialAdapterTest {
    @Test
    void mapsAllSixPureOreMaterialsExplicitly() {
        Map<LegacyOreMaterial, Material> expected = Map.of(
                LegacyOreMaterial.COAL_ORE, Material.COAL_ORE,
                LegacyOreMaterial.IRON_ORE, Material.IRON_ORE,
                LegacyOreMaterial.GOLD_ORE, Material.GOLD_ORE,
                LegacyOreMaterial.REDSTONE_ORE, Material.REDSTONE_ORE,
                LegacyOreMaterial.DIAMOND_ORE, Material.DIAMOND_ORE,
                LegacyOreMaterial.LAPIS_ORE, Material.LAPIS_ORE);

        for (LegacyOreMaterial material : LegacyOreMaterial.values()) {
            assertEquals(expected.get(material), LegacyOreMaterialAdapter.toMaterial(material));
        }
        EnumSet<Material> mapped = EnumSet.noneOf(Material.class);
        expected.values().forEach(mapped::add);
        assertFalse(mapped.stream().anyMatch(material -> material.name().startsWith("DEEPSLATE_")));
    }

    @Test
    void rejectsNullWithoutFallback() {
        assertThrows(NullPointerException.class, () -> LegacyOreMaterialAdapter.toMaterial(null));
    }
}
