package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class LegacyUndergroundPopulatorTest {
    @Test
    void overridesOnlyTheNonDeprecatedLimitedRegionPopulateMethod() throws Exception {
        Method method = LegacyUndergroundPopulator.class.getDeclaredMethod(
                "populate",
                WorldInfo.class,
                Random.class,
                int.class,
                int.class,
                LimitedRegion.class);
        assertEquals(void.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(1, Arrays.stream(LegacyUndergroundPopulator.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("populate"))
                .count());
        assertTrue(BlockPopulator.class.isAssignableFrom(LegacyUndergroundPopulator.class));
    }

    @Test
    void hasOnlyImmutableSharedServicesAndValidatesPaperBoundaries() {
        for (Field field : LegacyUndergroundPopulator.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        LegacyUndergroundPopulator populator = new LegacyUndergroundPopulator();
        assertThrows(NullPointerException.class,
                () -> populator.populate(null, new Random(1), 0, 0, null));
    }

    @Test
    void appliesAllGeologyBeforeAnyOreOnTheSameCallScopedAccess() {
        OrderingWorld world = new OrderingWorld(0, 0, -64, 320);
        var summary = LegacyUndergroundPopulator.applyUnderground(
                1_165_2021L, 0, 0, -64, 320, world);

        assertTrue(summary.geology().applied() > 0);
        assertTrue(summary.ore().applied() > 0);
        assertTrue(world.lastGeologyWrite < world.firstOreWrite);
    }

    @Test
    void ignoresThePassedPaperRandomAndUsesTheWorldInfoSeed() {
        LegacyUndergroundPopulator populator = new LegacyUndergroundPopulator();
        PopulatedResult first = populateWith(populator, new Random(1L));
        Random differentlyAdvanced = new Random(999L);
        differentlyAdvanced.nextLong();
        differentlyAdvanced.nextInt();
        PopulatedResult second = populateWith(populator, differentlyAdvanced);
        assertEquals(first, second);
    }

    @Test
    void reusesTheSamePopulatorConcurrentlyAcrossIndependentRegions() throws Exception {
        LegacyUndergroundPopulator populator = new LegacyUndergroundPopulator();
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<PopulatedResult> task = () -> populateWith(populator, new Random());
            List<Callable<PopulatedResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                tasks.add(task);
            }
            PopulatedResult expected = task.call();
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static PopulatedResult populateWith(
            LegacyUndergroundPopulator populator, Random random) {
        InMemoryGeologyWorld world = new InMemoryGeologyWorld(0, 0, -64, 320);
        LimitedRegion region = (LimitedRegion) Proxy.newProxyInstance(
                LimitedRegion.class.getClassLoader(),
                new Class<?>[] {LimitedRegion.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isInRegion" -> world.isAccessible(
                            (int) arguments[0], (int) arguments[1], (int) arguments[2]);
                    case "getType" -> world.getMaterial(
                            (int) arguments[0], (int) arguments[1], (int) arguments[2]);
                    case "setType" -> {
                        world.setInitialMaterial(
                                (int) arguments[0],
                                (int) arguments[1],
                                (int) arguments[2],
                                (Material) arguments[3]);
                        yield null;
                    }
                    case "getWorld" -> throw new AssertionError("getWorld must not be called");
                    default -> defaultValue(method.getReturnType());
                });
        WorldInfo worldInfo = (WorldInfo) Proxy.newProxyInstance(
                WorldInfo.class.getClassLoader(),
                new Class<?>[] {WorldInfo.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getSeed" -> 1_165_2021L;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    default -> defaultValue(method.getReturnType());
                });
        populator.populate(worldInfo, random, 0, 0, region);
        return new PopulatedResult(world.materialCounts(), world.checksum());
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

    private static final class OrderingWorld implements LegacyUndergroundBlockAccess {
        private final InMemoryGeologyWorld delegate;
        private int writes;
        private int lastGeologyWrite = -1;
        private int firstOreWrite = Integer.MAX_VALUE;

        private OrderingWorld(int chunkX, int chunkZ, int minimumY, int maximumYExclusive) {
            delegate = new InMemoryGeologyWorld(
                    chunkX, chunkZ, minimumY, maximumYExclusive);
        }

        @Override
        public boolean isAccessible(int x, int y, int z) {
            return delegate.isAccessible(x, y, z);
        }

        @Override
        public LegacyBlockKind getBlockKind(int x, int y, int z) {
            return delegate.getBlockKind(x, y, z);
        }

        @Override
        public void setMaterial(int x, int y, int z, LegacyGeologyMaterial material) {
            lastGeologyWrite = writes++;
            delegate.setMaterial(x, y, z, material);
        }

        @Override
        public void setMaterial(
                int x,
                int y,
                int z,
                net.nobu0707.legacyminingworld.ore.LegacyOreMaterial material) {
            firstOreWrite = Math.min(firstOreWrite, writes++);
            delegate.setMaterial(x, y, z, material);
        }
    }

    private record PopulatedResult(Map<Material, Integer> counts, long checksum) {
    }
}
