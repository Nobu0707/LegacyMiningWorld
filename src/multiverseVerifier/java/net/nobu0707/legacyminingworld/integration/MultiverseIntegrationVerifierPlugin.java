package net.nobu0707.legacyminingworld.integration;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class MultiverseIntegrationVerifierPlugin extends JavaPlugin
        implements CommandExecutor {
    private final WorldVerifier verifier = new WorldVerifier();

    @Override
    public void onEnable() {
        var command = getCommand("lmwit");
        if (command == null) {
            throw new IllegalStateException("lmwit command is missing from test plugin metadata");
        }
        command.setExecutor(this);
        getLogger().info("LegacyMiningWorld Multiverse test-only verifier "
                + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof ConsoleCommandSender)) {
            fail("console_only");
            return true;
        }
        if (!Bukkit.isPrimaryThread()) {
            fail("not_primary_thread");
            return true;
        }
        if (arguments.length != 3 || !arguments[0].equalsIgnoreCase("verify")) {
            fail("usage_lmwit_verify_world_seed");
            return true;
        }

        String worldName = arguments[1];
        try {
            long expectedSeed = VerifierSupport.parseExpectedSeed(arguments[2]);
            getLogger().info("LMW_MV_VERIFY_BEGIN world=" + worldName
                    + " expectedSeed=" + expectedSeed);
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new VerificationException("world_not_loaded_" + worldName);
            }
            verifier.verify(world, worldName, expectedSeed, getLogger()::info);
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE,
                    "LMW_MV_VERIFY_FAIL reason=" + safeReason(throwable), throwable);
        }
        return true;
    }

    private void fail(String reason) {
        getLogger().severe("LMW_MV_VERIFY_FAIL reason=" + reason);
    }

    private static String safeReason(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
