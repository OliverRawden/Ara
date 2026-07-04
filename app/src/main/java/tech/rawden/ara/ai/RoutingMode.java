package tech.rawden.ara.ai;

/** User-controlled model routing preference for the current session. */
public enum RoutingMode {
    AUTO,
    LIGHT_ONLY,
    HEAVY_ONLY;

    public String displayName() {
        return switch (this) {
            case AUTO -> "Automatic";
            case LIGHT_ONLY -> "Fast only";
            case HEAVY_ONLY -> "Advanced only";
        };
    }

    public static RoutingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return RoutingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}