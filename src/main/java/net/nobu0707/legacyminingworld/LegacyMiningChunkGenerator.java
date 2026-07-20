package net.nobu0707.legacyminingworld;

import java.util.Random;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

final class LegacyMiningChunkGenerator extends ChunkGenerator {
    private static final int CHUNK_SIZE = 16;

    @Override
    public void generateNoise(
            WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        validateHeightRange(chunkData.getMinHeight(), chunkData.getMaxHeight());
        chunkData.setRegion(
                0,
                LegacyTerrainProfile.BEDROCK_MIN_Y,
                0,
                CHUNK_SIZE,
                LegacyTerrainProfile.STONE_MAX_Y + 1,
                CHUNK_SIZE,
                Material.STONE);
    }

    @Override
    public void generateSurface(
            WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        validateHeightRange(chunkData.getMinHeight(), chunkData.getMaxHeight());
        chunkData.setRegion(
                0,
                LegacyTerrainProfile.DIRT_MIN_Y,
                0,
                CHUNK_SIZE,
                LegacyTerrainProfile.DIRT_MAX_Y + 1,
                CHUNK_SIZE,
                Material.DIRT);
        chunkData.setRegion(
                0,
                LegacyTerrainProfile.SURFACE_Y,
                0,
                CHUNK_SIZE,
                LegacyTerrainProfile.SURFACE_Y + 1,
                CHUNK_SIZE,
                Material.GRASS_BLOCK);
    }

    @Override
    public void generateBedrock(
            WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        validateHeightRange(chunkData.getMinHeight(), chunkData.getMaxHeight());
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int y = LegacyTerrainProfile.BEDROCK_MIN_Y;
                        y <= LegacyTerrainProfile.BEDROCK_MAX_Y;
                        y++) {
                    Material material = LegacyBedrockPattern.isBedrock(y, random)
                            ? Material.BEDROCK
                            : Material.STONE;
                    chunkData.setBlock(x, y, z, material);
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return PlainsBiomeProvider.INSTANCE;
    }

    @Override
    public int getBaseHeight(
            WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        validateHeightRange(worldInfo.getMinHeight(), worldInfo.getMaxHeight());
        return LegacyTerrainProfile.STONE_MAX_Y;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, LegacyTerrainProfile.SURFACE_Y + 1.0, 0.5);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    private static void validateHeightRange(int minHeight, int maxHeight) {
        if (minHeight > LegacyTerrainProfile.BEDROCK_MIN_Y
                || maxHeight <= LegacyTerrainProfile.SURFACE_Y) {
            throw new IllegalArgumentException("World height range [" + minHeight + ", "
                    + maxHeight + ") cannot contain required terrain Y=0..70");
        }
    }
}
