package net.nobu0707.legacyminingworld.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Random;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("geology-adapter")
class LegacyGeologyPopulatorTest {
    @Test
    void overridesOnlyTheNonDeprecatedLimitedRegionPopulateMethod() throws Exception {
        Method method = LegacyGeologyPopulator.class.getDeclaredMethod(
                "populate",
                WorldInfo.class,
                Random.class,
                int.class,
                int.class,
                LimitedRegion.class);
        assertEquals(void.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(1, Arrays.stream(LegacyGeologyPopulator.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("populate"))
                .count());
        assertTrue(BlockPopulator.class.isAssignableFrom(LegacyGeologyPopulator.class));
    }

    @Test
    void hasNoMutableOrCallScopedSharedStateAndValidatesPaperBoundaries() {
        for (Field field : LegacyGeologyPopulator.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        LegacyGeologyPopulator populator = new LegacyGeologyPopulator();
        assertThrows(NullPointerException.class,
                () -> populator.populate(null, new Random(1), 0, 0, null));
    }
}
