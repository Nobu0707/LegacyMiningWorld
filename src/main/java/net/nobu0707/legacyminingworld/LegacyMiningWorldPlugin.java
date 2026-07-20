package net.nobu0707.legacyminingworld;

import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyMiningWorldPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        getLogger().info(identity() + " Phase 0 foundation loaded.");
    }

    @Override
    public void onEnable() {
        getLogger().info(identity() + " enabled; the world generator is not implemented in Phase 0.");
    }

    @Override
    public void onDisable() {
        getLogger().info(identity() + " disabled.");
    }

    private String identity() {
        return getPluginMeta().getName() + " " + getPluginMeta().getVersion();
    }
}
