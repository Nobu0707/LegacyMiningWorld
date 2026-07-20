package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("geology-adapter")
class LegacyGeologyMaterialAdapterTest {
    @Test
    void convertsEveryGeneratedMaterialExplicitly() {
        assertEquals(Material.DIRT,
                LegacyGeologyMaterialAdapter.toMaterial(LegacyGeologyMaterial.DIRT));
        assertEquals(Material.GRAVEL,
                LegacyGeologyMaterialAdapter.toMaterial(LegacyGeologyMaterial.GRAVEL));
        assertEquals(Material.GRANITE,
                LegacyGeologyMaterialAdapter.toMaterial(LegacyGeologyMaterial.GRANITE));
        assertEquals(Material.DIORITE,
                LegacyGeologyMaterialAdapter.toMaterial(LegacyGeologyMaterial.DIORITE));
        assertEquals(Material.ANDESITE,
                LegacyGeologyMaterialAdapter.toMaterial(LegacyGeologyMaterial.ANDESITE));
        assertThrows(NullPointerException.class,
                () -> LegacyGeologyMaterialAdapter.toMaterial(null));
    }

    @Test
    void recognizesOnlyTheFourNaturalStoneReplacementTargets() {
        for (Material material : List.of(
                Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE)) {
            assertTrue(LegacyReplaceableBlock.isReplaceable(
                    LegacyGeologyMaterialAdapter.toBlockKind(material)), material::name);
        }

        for (Material material : List.of(
                Material.BEDROCK,
                Material.AIR,
                Material.CAVE_AIR,
                Material.VOID_AIR,
                Material.GRASS_BLOCK,
                Material.DIRT,
                Material.COARSE_DIRT,
                Material.ROOTED_DIRT,
                Material.GRAVEL,
                Material.WATER,
                Material.LAVA,
                Material.DEEPSLATE,
                Material.TUFF,
                Material.CALCITE,
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.DIAMOND_ORE,
                Material.LAPIS_ORE,
                Material.EMERALD_ORE,
                Material.COPPER_ORE,
                Material.DEEPSLATE_DIAMOND_ORE,
                Material.OAK_LOG)) {
            assertFalse(LegacyReplaceableBlock.isReplaceable(
                    LegacyGeologyMaterialAdapter.toBlockKind(material)), material::name);
        }
        assertThrows(NullPointerException.class,
                () -> LegacyGeologyMaterialAdapter.toBlockKind(null));
    }
}
