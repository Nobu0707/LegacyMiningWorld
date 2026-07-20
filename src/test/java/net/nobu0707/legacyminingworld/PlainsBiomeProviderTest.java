package net.nobu0707.legacyminingworld;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.bukkit.generator.BiomeProvider;
import org.junit.jupiter.api.Test;

class PlainsBiomeProviderTest {
    @Test
    void isAConcreteBiomeProvider() {
        assertTrue(BiomeProvider.class.isAssignableFrom(PlainsBiomeProvider.class));
    }

    @Test
    void hasNoMutableSharedFields() {
        for (Field field : PlainsBiomeProvider.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }
}
