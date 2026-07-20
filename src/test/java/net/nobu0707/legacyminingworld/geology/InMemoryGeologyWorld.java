package net.nobu0707.legacyminingworld.geology;

import java.util.EnumMap;
import java.util.Map;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterial;
import net.nobu0707.legacyminingworld.ore.LegacyOreMaterialAdapter;
import org.bukkit.Material;

final class InMemoryGeologyWorld implements LegacyUndergroundBlockAccess {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int targetChunkX;
    private final int targetChunkZ;
    private final int minimumY;
    private final int maximumYExclusive;
    private final Material[] blocks;
    private final boolean[] denied;
    private int getCalls;
    private int setCalls;
    private int inaccessibleGetOrSetCalls;

    InMemoryGeologyWorld(
            int targetChunkX, int targetChunkZ, int minimumY, int maximumYExclusive) {
        this.targetChunkX = targetChunkX;
        this.targetChunkZ = targetChunkZ;
        this.minimumY = minimumY;
        this.maximumYExclusive = maximumYExclusive;
        int volume = LegacyGeologySettings.CHUNK_SIZE
                * LegacyGeologySettings.CHUNK_SIZE
                * (maximumYExclusive - minimumY);
        blocks = new Material[volume];
        denied = new boolean[volume];
        initializeLegacyTerrain();
    }

    @Override
    public boolean isAccessible(int x, int y, int z) {
        return contains(x, y, z) && !denied[index(x, y, z)];
    }

    @Override
    public LegacyBlockKind getBlockKind(int x, int y, int z) {
        if (!isAccessible(x, y, z)) {
            inaccessibleGetOrSetCalls++;
            throw new AssertionError("getBlockKind called outside the accessible region");
        }
        getCalls++;
        return LegacyGeologyMaterialAdapter.toBlockKind(blocks[index(x, y, z)]);
    }

    @Override
    public void setMaterial(int x, int y, int z, LegacyGeologyMaterial material) {
        if (!isAccessible(x, y, z)) {
            inaccessibleGetOrSetCalls++;
            throw new AssertionError("setMaterial called outside the accessible region");
        }
        setCalls++;
        blocks[index(x, y, z)] = LegacyGeologyMaterialAdapter.toMaterial(material);
    }

    @Override
    public void setMaterial(int x, int y, int z, LegacyOreMaterial material) {
        if (!isAccessible(x, y, z)) {
            inaccessibleGetOrSetCalls++;
            throw new AssertionError("setMaterial called outside the accessible region");
        }
        setCalls++;
        blocks[index(x, y, z)] = LegacyOreMaterialAdapter.toMaterial(material);
    }

    Material getMaterial(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    void setInitialMaterial(int x, int y, int z, Material material) {
        blocks[index(x, y, z)] = material;
    }

    void deny(int x, int y, int z) {
        denied[index(x, y, z)] = true;
    }

    int getCalls() {
        return getCalls;
    }

    int setCalls() {
        return setCalls;
    }

    int inaccessibleGetOrSetCalls() {
        return inaccessibleGetOrSetCalls;
    }

    Map<Material, Integer> materialCounts() {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (Material material : blocks) {
            counts.merge(material, 1, Integer::sum);
        }
        return counts;
    }

    long checksum() {
        long checksum = FNV_OFFSET_BASIS;
        int minimumX = targetChunkX << 4;
        int minimumZ = targetChunkZ << 4;
        for (int y = minimumY; y < maximumYExclusive; y++) {
            for (int z = minimumZ; z < minimumZ + LegacyGeologySettings.CHUNK_SIZE; z++) {
                for (int x = minimumX; x < minimumX + LegacyGeologySettings.CHUNK_SIZE; x++) {
                    checksum = mix(checksum, x);
                    checksum = mix(checksum, y);
                    checksum = mix(checksum, z);
                    for (char character : getMaterial(x, y, z).name().toCharArray()) {
                        checksum = mix(checksum, character);
                    }
                }
            }
        }
        return checksum;
    }

    private void initializeLegacyTerrain() {
        int minimumX = targetChunkX << 4;
        int minimumZ = targetChunkZ << 4;
        for (int y = minimumY; y < maximumYExclusive; y++) {
            for (int z = minimumZ; z < minimumZ + LegacyGeologySettings.CHUNK_SIZE; z++) {
                for (int x = minimumX; x < minimumX + LegacyGeologySettings.CHUNK_SIZE; x++) {
                    blocks[index(x, y, z)] = initialMaterial(x, y, z);
                }
            }
        }
    }

    private static Material initialMaterial(int x, int y, int z) {
        if (y < 0 || y > 70) {
            return Material.AIR;
        }
        if (y == 0) {
            return Material.BEDROCK;
        }
        if (y <= 4) {
            int testRandomValue = Math.floorMod(x * 31 + z * 17, 5);
            return y <= testRandomValue ? Material.BEDROCK : Material.STONE;
        }
        if (y <= 67) {
            return Material.STONE;
        }
        if (y <= 69) {
            return Material.DIRT;
        }
        return Material.GRASS_BLOCK;
    }

    private boolean contains(int x, int y, int z) {
        int minimumX = targetChunkX << 4;
        int minimumZ = targetChunkZ << 4;
        return x >= minimumX
                && x < minimumX + LegacyGeologySettings.CHUNK_SIZE
                && z >= minimumZ
                && z < minimumZ + LegacyGeologySettings.CHUNK_SIZE
                && y >= minimumY
                && y < maximumYExclusive;
    }

    private int index(int x, int y, int z) {
        int localX = x - (targetChunkX << 4);
        int localZ = z - (targetChunkZ << 4);
        int localY = y - minimumY;
        return localX
                + localZ * LegacyGeologySettings.CHUNK_SIZE
                + localY * LegacyGeologySettings.CHUNK_SIZE * LegacyGeologySettings.CHUNK_SIZE;
    }

    private static long mix(long checksum, int value) {
        return (checksum ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
    }
}
