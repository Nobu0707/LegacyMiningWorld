package net.nobu0707.legacyminingworld.integration;

import java.util.Arrays;
import java.util.Locale;

record GridStatistics(
        long total,
        double mean,
        double populationStandardDeviation,
        long minimum,
        long maximum,
        double median,
        long p95,
        long zeroCount) {
    static GridStatistics calculate(long[] input) {
        if (input.length == 0) {
            throw new IllegalArgumentException("statistics_require_values");
        }
        long[] values = input.clone();
        Arrays.sort(values);
        long total = 0;
        long zeroCount = 0;
        for (long value : values) {
            total = Math.addExact(total, value);
            if (value == 0) {
                zeroCount++;
            }
        }
        double mean = (double) total / values.length;
        double squared = 0.0;
        for (long value : values) {
            double difference = value - mean;
            squared += difference * difference;
        }
        double median = values.length % 2 == 0
                ? (values[values.length / 2 - 1] + values[values.length / 2]) / 2.0
                : values[values.length / 2];
        int p95Index = Math.max(0, (int) Math.ceil(values.length * 0.95) - 1);
        return new GridStatistics(total, mean, Math.sqrt(squared / values.length),
                values[0], values[values.length - 1], median, values[p95Index], zeroCount);
    }

    String format() {
        return String.format(Locale.ROOT,
                "total=%d mean=%.3f populationStdDev=%.3f min=%d max=%d median=%.3f p95=%d zeroChunks=%d",
                total, mean, populationStandardDeviation, minimum, maximum, median, p95, zeroCount);
    }
}
