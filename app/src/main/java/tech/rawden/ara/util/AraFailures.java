package tech.rawden.ara.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Centralized exception translation for user-facing and log-friendly error messages.
 *
 * <p>Call sites throw the returned exception (or wrap and rethrow) so UI layers see consistent
 * wording without duplicating string templates.
 *
 * <p><b>Thread-safety:</b> stateless.
 */
public final class AraFailures {

    private AraFailures() {}

    /**
     * Model load failed (mmap, GPU init, corrupt GGUF).
     *
     * @apiNote Thrown from {@link tech.rawden.ara.ai.LlamaCppInferenceService#loadModel}.
     */
    public static IOException modelLoad(Path modelPath, Throwable cause) {
        var name = modelPath != null ? modelPath.getFileName() : "unknown";
        return new IOException(
                "Could not load model \"" + name + "\". "
                        + "Check free RAM, try the light model, or re-download the GGUF.",
                cause);
    }

    /**
     * GGUF download or assembly failed.
     */
    public static IOException modelDownload(String filename, Throwable cause) {
        return new IOException(
                "Model download failed for \"" + nullToUnknown(filename) + "\". "
                        + "Check your network connection and try again.",
                cause);
    }

    /**
     * Remote metadata fetch failed (models.json / latest.json).
     */
    public static IOException metadataFetch(String url, Throwable cause) {
        return new IOException(
                "Could not fetch metadata from " + nullToUnknown(url) + ". "
                        + "You can work offline with cached or local files.",
                cause);
    }

    /**
     * Chat history read/write failed.
     */
    public static IOException chatPersistence(String operation, Throwable cause) {
        return new IOException("Chat " + operation + " failed: " + rootMessage(cause), cause);
    }

    /**
     * Settings read/write failed.
     */
    public static IOException settingsPersistence(String operation, Throwable cause) {
        return new IOException("Settings " + operation + " failed: " + rootMessage(cause), cause);
    }

    /**
     * Inference generation failed mid-stream.
     */
    public static RuntimeException inference(String phase, Throwable cause) {
        return new RuntimeException("Inference " + phase + " failed: " + rootMessage(cause), cause);
    }

    private static String nullToUnknown(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    private static String rootMessage(Throwable cause) {
        if (cause == null) {
            return "unknown error";
        }
        var msg = cause.getMessage();
        return msg != null && !msg.isBlank() ? msg : cause.getClass().getSimpleName();
    }
}