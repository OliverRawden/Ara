package tech.rawden.ara.util;

import tech.rawden.ara.core.AraConfig;
import tech.rawden.ara.core.AppLog;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * Lightweight exponential-backoff retry helper for transient I/O and HTTP failures.
 *
 * <p>Prefer this over adding a full resilience library for the few network-bound paths in Ara
 * (model catalog fetch, GGUF part downloads, update metadata).
 *
 * <p><b>Thread-safety:</b> stateless; safe to call from virtual threads.
 */
public final class RetryExecutor {

    private static final Logger LOG = AppLog.of("retry");

    private RetryExecutor() {}

    /**
     * Runs {@code action} with the default {@link AraConfig#httpRetryPolicy()}.
     *
     * @throws Exception the last failure if all attempts are exhausted
     */
    public static <T> T run(String label, Callable<T> action) throws Exception {
        return run(label, AraConfig.httpRetryPolicy(), action);
    }

    /**
     * Runs {@code action} with the given policy, retrying only when {@link #isRetryable(Throwable)}
     * returns true.
     */
    public static <T> T run(String label, AraConfig.RetryPolicy policy, Callable<T> action) throws Exception {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(policy, "policy");
        int maxAttempts = Math.max(1, policy.maxAttempts());
        Duration delay = policy.initialDelay();
        Exception last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                last = e;
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                LOG.warning(label + " attempt " + attempt + "/" + maxAttempts + " failed: " + e.getMessage()
                        + " — retrying in " + delay.toMillis() + "ms");
                Thread.sleep(delay.toMillis());
                long nextMs = (long) (delay.toMillis() * policy.multiplier());
                delay = Duration.ofMillis(Math.min(nextMs, policy.maxDelay().toMillis()));
            }
        }
        throw last != null ? last : new IOException(label + " failed with no exception detail");
    }

    /** Runs a void action (wraps checked exceptions). */
    public static void runVoid(String label, AraConfig.RetryPolicy policy, RunnableWithException action)
            throws Exception {
        run(label, policy, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns true for likely-transient failures (timeouts, connection resets, HTTP 5xx).
     *
     * @implNote Non-retryable: HTTP 4xx (except 408/429), validation errors, SHA mismatches.
     */
    public static boolean isRetryable(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof InterruptedException) {
            return false;
        }
        var msg = t.getMessage();
        if (msg != null) {
            if (msg.contains("SHA-256") || msg.contains("does not match expected")) {
                return false;
            }
            if (msg.contains("HTTP 4") && !msg.contains("HTTP 408") && !msg.contains("HTTP 429")) {
                return false;
            }
            if (msg.contains("HTTP 5")) {
                return true;
            }
        }
        if (t instanceof IOException) {
            return true;
        }
        return t.getCause() != null && isRetryable(t.getCause());
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Exception;
    }
}