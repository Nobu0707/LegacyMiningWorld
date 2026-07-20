package net.nobu0707.legacyminingworld.geology;

import java.util.Objects;
import org.bukkit.Material;

/** Explicit conversion boundary between Bukkit materials and the pure geology model. */
public final class LegacyGeologyMaterialAdapter {
    private LegacyGeologyMaterialAdapter() {
    }

    public static LegacyBlockKind toBlockKind(Material material) {
        return switch (Objects.requireNonNull(material, "material")) {
            case STONE -> LegacyBlockKind.STONE;
            case GRANITE -> LegacyBlockKind.GRANITE;
            case DIORITE -> LegacyBlockKind.DIORITE;
            case ANDESITE -> LegacyBlockKind.ANDESITE;
            case BEDROCK -> LegacyBlockKind.BEDROCK;
            case AIR, CAVE_AIR, VOID_AIR -> LegacyBlockKind.AIR;
            case GRASS_BLOCK -> LegacyBlockKind.GRASS_BLOCK;
            case DIRT, COARSE_DIRT, ROOTED_DIRT -> LegacyBlockKind.DIRT;
            case GRAVEL -> LegacyBlockKind.GRAVEL;
            case WATER -> LegacyBlockKind.WATER;
            case LAVA -> LegacyBlockKind.LAVA;
            case DEEPSLATE, TUFF, CALCITE -> LegacyBlockKind.DEEPSLATE;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> LegacyBlockKind.COAL_ORE;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> LegacyBlockKind.IRON_ORE;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> LegacyBlockKind.GOLD_ORE;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> LegacyBlockKind.REDSTONE_ORE;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> LegacyBlockKind.DIAMOND_ORE;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> LegacyBlockKind.LAPIS_ORE;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> LegacyBlockKind.EMERALD_ORE;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> LegacyBlockKind.COPPER_ORE;
            default -> LegacyBlockKind.OTHER;
        };
    }

    public static Material toMaterial(LegacyGeologyMaterial material) {
        return switch (Objects.requireNonNull(material, "material")) {
            case DIRT -> Material.DIRT;
            case GRAVEL -> Material.GRAVEL;
            case GRANITE -> Material.GRANITE;
            case DIORITE -> Material.DIORITE;
            case ANDESITE -> Material.ANDESITE;
        };
    }
}
