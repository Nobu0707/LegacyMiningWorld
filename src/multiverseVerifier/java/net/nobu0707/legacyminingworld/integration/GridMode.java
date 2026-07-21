package net.nobu0707.legacyminingworld.integration;

enum GridMode {
    GENERATE,
    EXISTING;

    static GridMode parse(String value) {
        return switch (value) {
            case "generate" -> GENERATE;
            case "existing" -> EXISTING;
            default -> throw new VerificationException("invalid_grid_mode_" + value);
        };
    }

    String markerValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
