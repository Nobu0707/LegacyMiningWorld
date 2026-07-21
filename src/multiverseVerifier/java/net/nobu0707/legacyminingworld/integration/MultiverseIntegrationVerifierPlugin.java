package net.nobu0707.legacyminingworld.integration;

import java.io.IOException;
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
    private GridJob activeJob;

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
        try {
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("verify")) {
                verify(arguments[1], arguments[2]);
            } else if (arguments.length == 2
                    && arguments[0].equalsIgnoreCase("verify-vanilla-world")) {
                verifyVanillaWorld(arguments[1]);
            } else if (arguments.length == 6 && arguments[0].equalsIgnoreCase("grid")) {
                startGrid(arguments);
            } else if (arguments.length == 1
                    && arguments[0].equalsIgnoreCase("grid-status")) {
                gridStatus();
            } else if (arguments.length == 1
                    && arguments[0].equalsIgnoreCase("grid-cancel")) {
                gridCancel();
            } else {
                fail("usage_lmwit_command");
            }
        } catch (Throwable throwable) {
            String marker = arguments.length > 0
                    && arguments[0].toLowerCase(java.util.Locale.ROOT).startsWith("grid")
                    ? "LMW_GRID_FAIL report=" + reportIdForFailure(arguments)
                    : "LMW_MV_VERIFY_FAIL";
            getLogger().log(Level.SEVERE,
                    marker + " reason=" + VerifierSupport.safeReason(throwable), throwable);
        }
        return true;
    }

    @Override
    public void onDisable() {
        if (activeJob != null) {
            activeJob.cancel("plugin_disabled");
        }
    }

    private void verify(String worldName, String seedValue) throws IOException {
        long expectedSeed = VerifierSupport.parseExpectedSeed(seedValue);
        getLogger().info("LMW_MV_VERIFY_BEGIN world=" + worldName
                + " expectedSeed=" + expectedSeed);
        World world = requiredWorld(worldName);
        verifier.verify(world, worldName, expectedSeed, getLogger()::info);
    }

    private void verifyVanillaWorld(String worldName) {
        World world = requiredWorld(worldName);
        String generator = world.getGenerator() == null
                ? "null" : world.getGenerator().getClass().getName();
        String biomeProvider = world.getBiomeProvider() == null
                ? "null" : world.getBiomeProvider().getClass().getName();
        if (generator.equals("net.nobu0707.legacyminingworld.LegacyMiningChunkGenerator")) {
            throw new VerificationException("default_world_uses_legacy_generator");
        }
        if (biomeProvider.equals("net.nobu0707.legacyminingworld.PlainsBiomeProvider")) {
            throw new VerificationException("default_world_uses_legacy_biome_provider");
        }
        getLogger().info("LMW_VANILLA_WORLD_PASS world=" + worldName
                + " generator=" + generator + " biomeProvider=" + biomeProvider
                + " folder=" + world.getWorldFolder().getName());
    }

    private void startGrid(String[] arguments) throws IOException {
        if (activeJob != null) throw new VerificationException("grid_job_already_active");
        LargeScaleGridSpec spec = LargeScaleGridSpec.loadRequired(getClass());
        String worldName = arguments[1];
        long expectedSeed = VerifierSupport.parseExpectedSeed(arguments[2]);
        GridMode mode = GridMode.parse(arguments[3]);
        GridOrder order = GridOrder.parse(arguments[4]);
        String reportId = arguments[5];
        if (!GridReportWriter.isSafeReportId(reportId)) {
            throw new VerificationException("unsafe_report_id");
        }
        if (!spec.worldName().equals(worldName) || spec.seed() != expectedSeed) {
            throw new VerificationException("grid_command_does_not_match_spec");
        }
        World world = requiredWorld(worldName);
        if (world.getSeed() != expectedSeed
                || world.getEnvironment() != World.Environment.NORMAL
                || world.getMinHeight() != spec.minimumY()
                || world.getMaxHeight() != spec.maximumYExclusive()) {
            throw new VerificationException("grid_world_metadata_mismatch");
        }
        if (world.getGenerator() == null || !world.getGenerator().getClass().getName().equals(
                "net.nobu0707.legacyminingworld.LegacyMiningChunkGenerator")) {
            throw new VerificationException("grid_generator_mismatch");
        }
        activeJob = new GridJob(
                world, spec, mode, order, reportId,
                new GridReportWriter(getDataFolder().toPath()),
                getLogger()::info, this::releaseJob);
        getLogger().info("LMW_GRID_BEGIN report=" + reportId + " world=" + worldName
                + " chunks=" + spec.chunkCount() + " mode=" + mode.markerValue()
                + " order=" + order.markerValue());
        activeJob.attach(Bukkit.getScheduler().runTaskTimer(this, activeJob, 1L, 1L));
    }

    private void gridStatus() {
        if (activeJob == null) {
            getLogger().info("LMW_GRID_STATUS state=idle");
        } else {
            getLogger().info("LMW_GRID_STATUS state="
                    + activeJob.state().name().toLowerCase(java.util.Locale.ROOT)
                    + " report=" + activeJob.reportId()
                    + " completed=" + activeJob.completed());
        }
    }

    private void gridCancel() {
        if (activeJob == null) {
            getLogger().info("LMW_GRID_STATUS state=idle");
        } else {
            activeJob.cancel("cancelled_by_console");
        }
    }

    private World requiredWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) throw new VerificationException("world_not_loaded_" + name);
        return world;
    }

    private void releaseJob() {
        activeJob = null;
    }

    private static String reportIdForFailure(String[] arguments) {
        if (arguments.length == 6 && GridReportWriter.isSafeReportId(arguments[5])) {
            return arguments[5];
        }
        return "unknown";
    }

    private void fail(String reason) {
        getLogger().severe("LMW_MV_VERIFY_FAIL reason=" + reason);
    }

}
