package tech.rawden.ara.ai;

import tech.rawden.ara.core.AraConfig;
import tech.rawden.ara.core.AppLog;
import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.util.AraFailures;
import tech.rawden.ara.util.RetryExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * Chunked GGUF download with SHA-256 verification, exponential-backoff retries, and atomic placement.
 *
 * <p>Metadata comes from {@link ModelCatalog} / {@code installers/models.json}. Transient HTTP
 * failures are retried via {@link RetryExecutor}; checksum and size mismatches are not retried.
 *
 * <p><b>Thread-safety:</b> each instance is safe for single-threaded use per download; parallel
 * part downloads should use separate instances or serialized access.
 */
public final class ModelDownloader {

    private static final Logger LOG = AppLog.of("model");

    private static final Path MODELS_DIR = AraPaths.modelsDir();

    private final HttpClient httpClient;
    private final int chunkSizeBytes;

    public ModelDownloader(HttpClient httpClient, int chunkSizeMb) {
        this.httpClient = httpClient;
        this.chunkSizeBytes = Math.max(1, chunkSizeMb) * 1024 * 1024;
    }

    public ModelDownloader(HttpClient httpClient) {
        this(httpClient, 12);
    }

    public void ensureModelsDirectory() throws IOException {
        Files.createDirectories(MODELS_DIR);
    }

    public Path modelsDirectory() {
        return MODELS_DIR;
    }

    public void download(ModelRelease.DefaultModel meta, ProgressCallback callback) throws IOException, InterruptedException {
        if (meta == null || meta.filename == null || meta.filename.isBlank()) {
            throw new IOException("Model metadata is missing a filename.");
        }
        try {
            ensureModelsDirectory();
            Path target = MODELS_DIR.resolve(meta.filename);
            Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");

            if (hasParts(meta)) {
                LOG.info("Downloading model from Ara repo (" + meta.parts.size() + " parts): " + meta.filename);
                downloadAndAssembleParts(meta, tempFile, callback);
            } else if (meta.downloadUrl != null && !meta.downloadUrl.isBlank()) {
                LOG.info("Downloading model from " + meta.downloadUrl);
                downloadUrlToFile(meta.downloadUrl, tempFile, meta.sizeBytes, callback);
            } else {
                throw new IOException("No download URL or parts defined for " + meta.filename + ".");
            }

            verifyDownload(tempFile, meta);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Download complete: " + target);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw AraFailures.modelDownload(meta.filename, e);
        }
    }

    public boolean isDownloadAvailable(ModelRelease.DefaultModel meta) {
        if (meta == null) {
            return false;
        }
        return hasParts(meta) || (meta.downloadUrl != null && !meta.downloadUrl.isBlank());
    }

    private static boolean hasParts(ModelRelease.DefaultModel meta) {
        return meta.parts != null && !meta.parts.isEmpty();
    }

    private void downloadAndAssembleParts(ModelRelease.DefaultModel meta, Path tempFile, ProgressCallback callback)
            throws IOException, InterruptedException {
        long total = expectedTotalBytes(meta);
        long downloaded = 0;
        MessageDigest digest = newMessageDigest();

        try (OutputStream out = new DigestOutputStream(Files.newOutputStream(tempFile), digest)) {
            for (var part : meta.parts) {
                LOG.info("Downloading part: " + part.filename);
                downloaded = downloadUrlToStream(part.url, out, downloaded, total, callback);
            }
        }

        if (meta.sha256 != null && !meta.sha256.isBlank()) {
            var actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(meta.sha256)) {
                Files.deleteIfExists(tempFile);
                throw new IOException("SHA-256 mismatch after assembling model parts.");
            }
        }
    }

    private void downloadUrlToFile(String url, Path dest, long expectedSize, ProgressCallback callback)
            throws IOException, InterruptedException {
        try (OutputStream out = Files.newOutputStream(dest)) {
            downloadUrlToStream(url, out, 0, expectedSize > 0 ? expectedSize : -1, callback);
        }
    }

    private long downloadUrlToStream(
            String url, OutputStream out, long downloadedSoFar, long totalBytes, ProgressCallback callback)
            throws IOException, InterruptedException {
        try {
            return RetryExecutor.run(
                    "model-part:" + url,
                    () -> downloadUrlToStreamOnce(url, out, downloadedSoFar, totalBytes, callback));
        } catch (IOException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            if (e.getCause() instanceof InterruptedException ie) {
                throw ie;
            }
            throw new IOException("Model part download failed: " + url, e);
        }
    }

    private long downloadUrlToStreamOnce(
            String url, OutputStream out, long downloadedSoFar, long totalBytes, ProgressCallback callback)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(AraConfig.modelDownloadTimeout())
                .header("User-Agent", AraConfig.userAgent("model-download"))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed HTTP " + response.statusCode() + " for " + url);
        }

        long total = totalBytes;
        if (total < 0) {
            total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        }

        byte[] buf = new byte[chunkSizeBytes];
        long downloaded = downloadedSoFar;
        try (InputStream in = response.body()) {
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                downloaded += read;
                if (callback != null) {
                    callback.onProgress(downloaded, total);
                }
            }
        }
        return downloaded;
    }

    private static void verifyDownload(Path file, ModelRelease.DefaultModel meta) throws IOException {
        long size = Files.size(file);
        if (meta.sizeBytes > 0 && size != meta.sizeBytes) {
            Files.deleteIfExists(file);
            throw new IOException("Downloaded size " + size + " does not match expected " + meta.sizeBytes);
        }
        if (meta.sha256 != null && !meta.sha256.isBlank()) {
            String actual = sha256Hex(file);
            if (!actual.equalsIgnoreCase(meta.sha256)) {
                Files.deleteIfExists(file);
                throw new IOException("SHA-256 mismatch for downloaded model.");
            }
        }
    }

    private static long expectedTotalBytes(ModelRelease.DefaultModel meta) {
        if (meta.sizeBytes > 0) {
            return meta.sizeBytes;
        }
        return meta.parts.stream().mapToLong(p -> p.sizeBytes).sum();
    }

    private static MessageDigest newMessageDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Could not hash model file", e);
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long bytesDownloaded, long totalBytes);
    }
}