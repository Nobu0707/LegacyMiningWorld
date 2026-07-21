package net.nobu0707.legacyminingworld.integration;

enum GridOrder {
    FORWARD,
    REVERSE;

    static GridOrder parse(String value) {
        return switch (value) {
            case "forward" -> FORWARD;
            case "reverse" -> REVERSE;
            default -> throw new VerificationException("invalid_grid_order_" + value);
        };
    }

    String markerValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
