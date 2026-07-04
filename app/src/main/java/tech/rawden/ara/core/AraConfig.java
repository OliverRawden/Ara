package tech.rawden.ara.core;

import tech.rawden.ara.update.AppVersion;

import java.time.Duration;

/**
 * Central application configuration: network endpoints, timeouts, retry policy, and routing defaults.
 *
 * <p>Values can be overridden at runtime via {@code -Dara.*} system properties (useful in developer
 * mode). User-facing preferences remain in {@link tech.rawden.ara.model.AppSettings} /
 * {@code settings.json}.
 *
 * <p><b>Thread-safety:</b> all accessors are stateless and safe to call from any thread.
 */
public final class AraConfig {

    private AraConfig() {}

    // --- Metadata URLs (same branch policy: main) ---

    public static final String UPDATE_METADATA_URL =
            "https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json";

    public static final String MODEL_METADATA_URL =
            "https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/models.json";

    // --- Network timeouts ---

    public static Duration metadataTimeout() {
        return durationSeconds("ara.metadata.timeout.seconds", 20);
    }

    public static Duration httpConnectTimeout() {
        return durationSeconds("ara.http.connect.timeout.seconds", 30);
    }

    public static Duration modelDownloadTimeout() {
        return durationHours("ara.model.download.timeout.hours", 6);
    }

    public static Duration updateDownloadTimeout() {
        return durationMinutes("ara.update.download.timeout.minutes", 30);
    }

    // --- Retry policy (transient HTTP / I/O) ---

    public static RetryPolicy httpRetryPolicy() {
        return new RetryPolicy(
                intProperty("ara.retry.maxAttempts", 4),
                durationMillis("ara.retry.initialDelay.ms", 500),
                doubleProperty("ara.retry.multiplier", 2.0),
                durationSeconds("ara.retry.maxDelay.seconds", 30));
    }

    // --- Routing ---

    public static long heavyIdleUnloadMinutes() {
        return longProperty("ara.routing.heavyIdleUnload.minutes", 10);
    }

    // --- Chat persistence ---

    /** Max sessions loaded at startup; remainder stay on disk until explicitly opened. */
    public static int chatLoadSessionLimit() {
        return intProperty("ara.chat.loadSessionLimit", 60);
    }

    /** Poll interval for settings hot-reload when developer mode is on. */
    public static Duration settingsReloadInterval() {
        return durationSeconds("ara.dev.settingsReload.seconds", 2);
    }

    // --- User-Agent ---

    public static String userAgent(String component) {
        return "Ara/" + AppVersion.current() + " (" + component + ")";
    }

    // --- Typed retry contract ---

    /**
     * Exponential backoff parameters for {@link tech.rawden.ara.util.RetryExecutor}.
     *
     * @param maxAttempts total tries including the first attempt
     * @param initialDelay delay before the second attempt
     * @param multiplier delay multiplier after each failure
     * @param maxDelay cap on computed delay
     */
    public record RetryPolicy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {}

    private static Duration durationSeconds(String key, long defaultSeconds) {
        return Duration.ofSeconds(longProperty(key, defaultSeconds));
    }

    private static Duration durationMinutes(String key, long defaultMinutes) {
        return Duration.ofMinutes(longProperty(key, defaultMinutes));
    }

    private static Duration durationHours(String key, long defaultHours) {
        return Duration.ofHours(longProperty(key, defaultHours));
    }

    private static Duration durationMillis(String key, long defaultMillis) {
        return Duration.ofMillis(longProperty(key, defaultMillis));
    }

    private static int intProperty(String key, int defaultValue) {
        return (int) longProperty(key, defaultValue);
    }

    private static double doubleProperty(String key, double defaultValue) {
        var raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longProperty(String key, long defaultValue) {
        var raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}