package net.nobu0707.legacyminingworld.integration;

import java.util.LinkedHashMap;
import java.util.Map;

final class VerifierSupport {
    private VerifierSupport() {
    }

    static long parseExpectedSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid_expected_seed", exception);
        }
    }

    static String marker(String name, Map<String, ?> values) {
        StringBuilder line = new StringBuilder(name);
        values.forEach((key, value) -> line.append(' ').append(key).append('=').append(value));
        return line.toString();
    }

    static Map<String, String> parseMarker(String line, String expectedMarker) {
        String[] tokens = line.trim().split(" +");
        if (tokens.length == 0 || !tokens[0].equals(expectedMarker)) {
            throw new IllegalArgumentException("unexpected marker: " + line);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < tokens.length; index++) {
            int separator = tokens[index].indexOf('=');
            if (separator <= 0 || separator == tokens[index].length() - 1) {
                throw new IllegalArgumentException("invalid marker field: " + tokens[index]);
            }
            String key = tokens[index].substring(0, separator);
            String value = tokens[index].substring(separator + 1);
            if (values.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate marker field: " + key);
            }
        }
        return Map.copyOf(values);
    }

    static String safeReason(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
