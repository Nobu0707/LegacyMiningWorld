package net.nobu0707.legacyminingworld;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class PhaseOneConfigurationTest {
    @Test
    void normalizesOnlySupportedGeneratorIds() {
        assertEquals("default", LegacyMiningWorldPlugin.normalizeSupportedId(null));
        assertEquals("default", LegacyMiningWorldPlugin.normalizeSupportedId(""));
        assertEquals("default", LegacyMiningWorldPlugin.normalizeSupportedId("   "));
        assertEquals("default", LegacyMiningWorldPlugin.normalizeSupportedId("DEFAULT"));
        assertEquals("default", LegacyMiningWorldPlugin.normalizeSupportedId(" Default "));
        assertNull(LegacyMiningWorldPlugin.normalizeSupportedId("legacy"));
    }

    @Test
    void mainClassIsAPaperPlugin() {
        assertTrue(JavaPlugin.class.isAssignableFrom(LegacyMiningWorldPlugin.class));
    }

    @Test
    void versionPropertyIsPresent() {
        String version = System.getProperty("legacyminingworld.version", "");
        assertFalse(version.isBlank());
        assertTrue(version.equals("0.2.0"));
    }

    @Test
    void processedPluginDescriptorContainsPhaseOneMetadata() throws IOException {
        String descriptor;
        try (InputStream input = getClass().getResourceAsStream("/plugin.yml")) {
            assertTrue(input != null, "processed plugin.yml must be on the test classpath");
            descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(descriptor.contains("name: LegacyMiningWorld"));
        assertTrue(descriptor.contains(
                "main: net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin"));
        assertTrue(descriptor.contains("api-version: '26.1.2'"));
        assertFalse(descriptor.contains("${version}"));
        assertTrue(descriptor.contains(
                "version: " + System.getProperty("legacyminingworld.version")));
    }
}
