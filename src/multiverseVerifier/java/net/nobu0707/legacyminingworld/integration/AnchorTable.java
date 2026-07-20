package net.nobu0707.legacyminingworld.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

final class AnchorTable {
    private static final String HEADER = "id\tx\ty\tz\texpected_material\tpurpose\tpair_id\t"
            + "source_chunk_x\tsource_chunk_z\tfeature\tattempt\tvein_sequence";

    private AnchorTable() {
    }

    static List<Anchor> loadRequiredResource(Class<?> owner, String resource) throws IOException {
        InputStream input = owner.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("missing required resource: " + resource);
        }
        return parse(input);
    }

    static List<Anchor> parse(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        List<Anchor> anchors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean headerSeen = false;
        try (var reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    if (!HEADER.equals(line)) {
                        throw new IOException("invalid anchor header: " + line);
                    }
                    headerSeen = true;
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length != 12) {
                    throw new IOException("invalid anchor column count: " + line);
                }
                if (!ids.add(columns[0])) {
                    throw new IOException("duplicate anchor id: " + columns[0]);
                }
                try {
                    anchors.add(new Anchor(
                            columns[0],
                            Integer.parseInt(columns[1]),
                            Integer.parseInt(columns[2]),
                            Integer.parseInt(columns[3]),
                            Material.valueOf(columns[4]),
                            columns[5],
                            columns[6],
                            Integer.parseInt(columns[7]),
                            Integer.parseInt(columns[8]),
                            columns[9],
                            Integer.parseInt(columns[10]),
                            Integer.parseInt(columns[11])));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("invalid anchor value: " + line, exception);
                }
            }
        }
        if (!headerSeen) {
            throw new IOException("missing anchor header");
        }
        if (anchors.isEmpty()) {
            throw new IOException("anchor table is empty");
        }
        return List.copyOf(anchors);
    }
}
