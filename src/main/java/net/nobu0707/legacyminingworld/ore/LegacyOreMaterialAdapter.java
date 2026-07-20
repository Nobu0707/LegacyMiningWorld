package net.nobu0707.legacyminingworld.ore;

import java.util.Objects;
import org.bukkit.Material;

/** Explicit conversion from pure legacy ore materials to Bukkit materials. */
public final class LegacyOreMaterialAdapter {
    private LegacyOreMaterialAdapter() {
    }

    public static Material toMaterial(LegacyOreMaterial material) {
        return switch (Objects.requireNonNull(material, "material")) {
            case COAL_ORE -> Material.COAL_ORE;
            case IRON_ORE -> Material.IRON_ORE;
            case GOLD_ORE -> Material.GOLD_ORE;
            case REDSTONE_ORE -> Material.REDSTONE_ORE;
            case DIAMOND_ORE -> Material.DIAMOND_ORE;
            case LAPIS_ORE -> Material.LAPIS_ORE;
        };
    }
}
