package net.nobu0707.legacyminingworld.integration;

record GridMetrics(
        long totalNanos,
        long prepareNanos,
        long scanNanos,
        long reportNanos,
        int generatedBeforeScan,
        int newlyGenerated,
        int missingExisting,
        int unloadFailures,
        int maximumLoadedChunks) {
}
