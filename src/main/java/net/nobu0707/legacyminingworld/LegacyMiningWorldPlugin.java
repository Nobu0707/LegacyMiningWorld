package net.nobu0707.legacyminingworld;

import java.util.Locale;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyMiningWorldPlugin extends JavaPlugin {
    private static final LegacyMiningChunkGenerator GENERATOR = new LegacyMiningChunkGenerator();
    private static final PlainsBiomeProvider BIOME_PROVIDER = PlainsBiomeProvider.INSTANCE;

    @Override
    public void onLoad() {
        getLogger().info(identity() + " generator services loaded.");
    }

    @Override
    public void onEnable() {
        getLogger().info(identity()
                + " enabled; basic terrain, legacy geology, and legacy ores are available.");
    }

    @Override
    public void onDisable() {
        getLogger().info(identity() + " disabled.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        LegacyMiningChunkGenerator generator = generatorForSupportedId(id);
        if (generator == null) {
            getLogger().warning("Unsupported generator id '" + id + "' for world '" + worldName + "'.");
            return null;
        }
        String normalizedId = normalizeSupportedId(id);
        getLogger().info("Generator requested for world '" + worldName + "' with id '"
                + normalizedId + "'.");
        return generator;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        if (normalizeSupportedId(id) == null) {
            getLogger().warning("Unsupported biome provider id '" + id + "' for world '"
                    + worldName + "'.");
            return null;
        }
        return BIOME_PROVIDER;
    }

    static String normalizeSupportedId(String id) {
        if (id == null || id.isBlank()) {
            return "default";
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("default") ? normalized : null;
    }

    static LegacyMiningChunkGenerator generatorForSupportedId(String id) {
        return normalizeSupportedId(id) == null ? null : GENERATOR;
    }

    private String identity() {
        return getPluginMeta().getName() + " " + getPluginMeta().getVersion();
    }
}
