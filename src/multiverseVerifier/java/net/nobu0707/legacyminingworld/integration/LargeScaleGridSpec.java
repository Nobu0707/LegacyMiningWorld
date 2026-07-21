package net.nobu0707.legacyminingworld.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

record LargeScaleGridSpec(
        String worldName,
        long seed,
        int minimumChunkX,
        int maximumChunkX,
        int minimumChunkZ,
        int maximumChunkZ,
        int chunkCount,
        int minimumY,
        int maximumYExclusive) {
    static final int REQUIRED_CHUNK_COUNT = 1_089;
    static final long REQUIRED_BLOCK_COUNT = 107_053_056L;
    private static final Pattern SAFE_WORLD_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Set<String> KEYS = Set.of(
            "worldName", "seed", "minimumChunkX", "maximumChunkX",
            "minimumChunkZ", "maximumChunkZ", "chunkCount", "minimumY",
            "maximumYExclusive");

    static LargeScaleGridSpec loadRequired(Class<?> owner) throws IOException {
        try (InputStream input = owner.getResourceAsStream("/large-scale-grid.properties")) {
            if (input == null) {
                throw new IOException("missing_large_scale_grid_properties");
            }
            return parse(input);
        }
    }

    static LargeScaleGridSpec parse(InputStream input) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    throw new IOException("invalid_spec_line_" + lineNumber);
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (!KEYS.contains(key)) {
                    throw new IOException("unknown_spec_key_" + key);
                }
                if (values.putIfAbsent(key, value) != null) {
                    throw new IOException("duplicate_spec_key_" + key);
                }
            }
        }
        if (!values.keySet().equals(KEYS)) {
            Set<String> missing = new java.util.TreeSet<>(KEYS);
            missing.removeAll(values.keySet());
            throw new IOException("missing_spec_keys_" + String.join("_", missing));
        }
        try {
            LargeScaleGridSpec spec = new LargeScaleGridSpec(
                    values.get("worldName"),
                    Long.parseLong(values.get("seed")),
                    Integer.parseInt(values.get("minimumChunkX")),
                    Integer.parseInt(values.get("maximumChunkX")),
                    Integer.parseInt(values.get("minimumChunkZ")),
                    Integer.parseInt(values.get("maximumChunkZ")),
                    Integer.parseInt(values.get("chunkCount")),
                    Integer.parseInt(values.get("minimumY")),
                    Integer.parseInt(values.get("maximumYExclusive")));
            spec.validate();
            return spec;
        } catch (NumberFormatException exception) {
            throw new IOException("invalid_spec_number", exception);
        }
    }

    private void validate() throws IOException {
        if (!SAFE_WORLD_NAME.matcher(worldName).matches()
                || worldName.contains("..") || worldName.contains("/") || worldName.contains("\\")) {
            throw new IOException("unsafe_world_name");
        }
        if (minimumChunkX > maximumChunkX || minimumChunkZ > maximumChunkZ
                || minimumY >= maximumYExclusive) {
            throw new IOException("invalid_spec_range");
        }
        long calculated = (long) (maximumChunkX - minimumChunkX + 1)
                * (maximumChunkZ - minimumChunkZ + 1);
        if (calculated != chunkCount || chunkCount != REQUIRED_CHUNK_COUNT) {
            throw new IOException("wrong_chunk_count_" + calculated + "_" + chunkCount);
        }
        if (blockCount() != REQUIRED_BLOCK_COUNT) {
            throw new IOException("wrong_block_count_" + blockCount());
        }
    }

    long blockCount() {
        return (long) chunkCount * 16 * 16 * (maximumYExclusive - minimumY);
    }

    List<ChunkKey> chunks(GridOrder order) {
        java.util.ArrayList<ChunkKey> chunks = new java.util.ArrayList<>(chunkCount);
        if (order == GridOrder.FORWARD) {
            for (int z = minimumChunkZ; z <= maximumChunkZ; z++) {
                for (int x = minimumChunkX; x <= maximumChunkX; x++) {
                    chunks.add(new ChunkKey(x, z));
                }
            }
        } else {
            for (int z = maximumChunkZ; z >= minimumChunkZ; z--) {
                for (int x = maximumChunkX; x >= minimumChunkX; x--) {
                    chunks.add(new ChunkKey(x, z));
                }
            }
        }
        return List.copyOf(chunks);
    }
}
