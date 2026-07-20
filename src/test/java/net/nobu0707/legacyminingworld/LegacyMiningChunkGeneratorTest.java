package net.nobu0707.legacyminingworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Random;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;

class LegacyMiningChunkGeneratorTest {
    private final LegacyMiningChunkGenerator generator = new LegacyMiningChunkGenerator();

    @Test
    void disablesOnlyRequiredVanillaGenerationStages() {
        assertFalse(generator.shouldGenerateNoise());
        assertFalse(generator.shouldGenerateSurface());
        assertFalse(generator.shouldGenerateCaves());
        assertFalse(generator.shouldGenerateDecorations());
        assertFalse(generator.shouldGenerateStructures());
        assertFalse(Arrays.stream(LegacyMiningChunkGenerator.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("shouldGenerateMobs")));
    }

    @Test
    void suppliesPhaseOneBaseHeightAndBiomeProvider() {
        WorldInfo worldInfo = worldInfo(-64, 320);
        assertEquals(67, generator.getBaseHeight(
                worldInfo, new Random(1), 123, -456, HeightMap.WORLD_SURFACE_WG));
        assertNotNull(generator.getDefaultBiomeProvider(worldInfo));
        assertSame(PlainsBiomeProvider.INSTANCE, generator.getDefaultBiomeProvider(worldInfo));
    }

    @Test
    void fixesSpawnAboveOriginSurface() {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType()));
        Location spawn = generator.getFixedSpawnLocation(world, new Random(1));
        assertSame(world, spawn.getWorld());
        assertEquals(0.5, spawn.getX());
        assertEquals(71.0, spawn.getY());
        assertEquals(0.5, spawn.getZ());
    }

    @Test
    void rejectsWorldsThatCannotContainTheProfile() {
        assertThrows(IllegalArgumentException.class, () -> generator.getBaseHeight(
                worldInfo(1, 320), new Random(1), 0, 0, HeightMap.WORLD_SURFACE_WG));
        assertThrows(IllegalArgumentException.class, () -> generator.getBaseHeight(
                worldInfo(-64, 70), new Random(1), 0, 0, HeightMap.WORLD_SURFACE_WG));
    }

    private static WorldInfo worldInfo(int minHeight, int maxHeight) {
        return (WorldInfo) Proxy.newProxyInstance(
                WorldInfo.class.getClassLoader(), new Class<?>[] {WorldInfo.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMinHeight" -> minHeight;
                    case "getMaxHeight" -> maxHeight;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
