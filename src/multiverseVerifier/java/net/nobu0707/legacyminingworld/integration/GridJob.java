package net.nobu0707.legacyminingworld.integration;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

final class GridJob implements Runnable {
    enum State { PREPARING, SCANNING, FINISHED, FAILED, CANCELLED }

    private final World world;
    private final LargeScaleGridSpec spec;
    private final GridMode mode;
    private final GridOrder order;
    private final String reportId;
    private final GridReportWriter writer;
    private final Consumer<String> output;
    private final Runnable release;
    private final List<ChunkKey> preparationOrder;
    private final List<ChunkKey> canonicalOrder;
    private final GridAccumulator accumulator;
    private final long startedNanos = System.nanoTime();
    private State state = State.PREPARING;
    private BukkitTask task;
    private int index;
    private int generatedBeforeScan;
    private int newlyGenerated;
    private int missingExisting;
    private int unloadFailures;
    private int maximumLoadedChunks;
    private long prepareNanos;
    private long scanNanos;

    GridJob(
            World world,
            LargeScaleGridSpec spec,
            GridMode mode,
            GridOrder order,
            String reportId,
            GridReportWriter writer,
            Consumer<String> output,
            Runnable release) {
        this.world = world;
        this.spec = spec;
        this.mode = mode;
        this.order = order;
        this.reportId = reportId;
        this.writer = writer;
        this.output = output;
        this.release = release;
        preparationOrder = spec.chunks(order);
        canonicalOrder = spec.chunks(GridOrder.FORWARD);
        accumulator = new GridAccumulator(spec);
    }

    void attach(BukkitTask task) {
        if (this.task != null) throw new IllegalStateException("task_already_attached");
        this.task = task;
    }

    @Override
    public void run() {
        long tickStarted = System.nanoTime();
        try {
            maximumLoadedChunks = Math.max(maximumLoadedChunks, world.getLoadedChunks().length);
            if (state == State.PREPARING) {
                prepareOne();
                prepareNanos += System.nanoTime() - tickStarted;
            } else if (state == State.SCANNING) {
                scanOne();
                scanNanos += System.nanoTime() - tickStarted;
            }
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    private void prepareOne() {
        ChunkKey key = preparationOrder.get(index);
        boolean generated = world.isChunkGenerated(key.x(), key.z());
        if (generated) {
            generatedBeforeScan++;
        } else if (mode == GridMode.EXISTING) {
            missingExisting++;
            throw new VerificationException("existing_chunk_missing_" + key);
        }
        Chunk chunk = world.getChunkAt(key.x(), key.z());
        maximumLoadedChunks = Math.max(maximumLoadedChunks, world.getLoadedChunks().length);
        if (!chunk.isGenerated()) {
            throw new VerificationException("chunk_not_generated_after_load_" + key);
        }
        if (!generated) newlyGenerated++;
        if (!world.unloadChunk(key.x(), key.z(), mode == GridMode.GENERATE)) {
            unloadFailures++;
        }
        index++;
        if (index == preparationOrder.size()) {
            if (mode == GridMode.EXISTING && newlyGenerated != 0) {
                throw new VerificationException("existing_mode_generated_chunks");
            }
            state = State.SCANNING;
            index = 0;
            output.accept("LMW_GRID_PREPARED report=" + reportId
                    + " generatedBefore=" + generatedBeforeScan
                    + " newlyGenerated=" + newlyGenerated
                    + " missingExisting=" + missingExisting);
        }
    }

    private void scanOne() throws IOException {
        ChunkKey key = canonicalOrder.get(index);
        if (!world.isChunkGenerated(key.x(), key.z())) {
            throw new VerificationException("generated_chunk_disappeared_" + key);
        }
        Chunk chunk = world.getChunkAt(key.x(), key.z());
        maximumLoadedChunks = Math.max(maximumLoadedChunks, world.getLoadedChunks().length);
        accumulator.scan(chunk.getChunkSnapshot(true, true, false, false), key);
        if (!world.unloadChunk(key.x(), key.z(), mode == GridMode.GENERATE)) {
            unloadFailures++;
        }
        index++;
        if (index % 100 == 0 || index == canonicalOrder.size()) {
            output.accept("LMW_GRID_PROGRESS report=" + reportId
                    + " completed=" + index + " total=" + canonicalOrder.size());
        }
        if (index == canonicalOrder.size()) finish();
    }

    private void finish() throws IOException {
        accumulator.validateComplete();
        long beforeReport = System.nanoTime();
        GridMetrics metrics = new GridMetrics(
                beforeReport - startedNanos,
                prepareNanos,
                scanNanos,
                0,
                generatedBeforeScan,
                newlyGenerated,
                missingExisting,
                unloadFailures,
                maximumLoadedChunks);
        metrics = writer.write(reportId, accumulator, metrics);
        state = State.FINISHED;
        stopTask();
        output.accept("LMW_GRID_PASS report=" + reportId
                + " world=" + world.getName()
                + " uid=" + world.getUID()
                + " chunks=" + spec.chunkCount()
                + " blocks=" + accumulator.scannedBlocks()
                + " y5_67Checksum=" + accumulator.combinedYFiveChecksum()
                + " fullChecksum=" + accumulator.combinedFullChecksum()
                + " forbidden=" + accumulator.forbidden()
                + " unknown=" + accumulator.unknownNonAir()
                + " biomes=" + accumulator.biomeChecks()
                + " newlyGenerated=" + newlyGenerated
                + " unloadFailures=" + unloadFailures
                + " elapsedNanos=" + metrics.totalNanos());
        release.run();
    }

    void cancel(String reason) {
        if (isTerminal()) return;
        state = State.CANCELLED;
        stopTask();
        output.accept("LMW_GRID_FAIL report=" + reportId + " reason=" + reason);
        release.run();
    }

    private void fail(Throwable throwable) {
        state = State.FAILED;
        stopTask();
        output.accept("LMW_GRID_FAIL report=" + reportId
                + " reason=" + VerifierSupport.safeReason(throwable));
        release.run();
    }

    private void stopTask() {
        if (task != null) task.cancel();
    }

    State state() { return state; }
    String reportId() { return reportId; }
    int completed() { return state == State.PREPARING ? 0 : index; }
    boolean isTerminal() {
        return state == State.FINISHED || state == State.FAILED || state == State.CANCELLED;
    }
}
