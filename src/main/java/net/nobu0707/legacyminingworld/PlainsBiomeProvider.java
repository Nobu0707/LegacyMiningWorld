package net.nobu0707.legacyminingworld;

import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

final class PlainsBiomeProvider extends BiomeProvider {
    static final PlainsBiomeProvider INSTANCE = new PlainsBiomeProvider();

    private PlainsBiomeProvider() {
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Biome.PLAINS;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(Biome.PLAINS);
    }
}
