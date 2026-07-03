package tech.rawden.ara.ai;

import tech.rawden.ara.core.AraPaths;

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
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Discovers, downloads, and resolves GGUF model files under {@code ~/Documents/Ara/models/}.
 * Default model metadata and download URLs come from {@code installers/models.json} on the Ara
 * GitHub repo (see {@link ModelCatalog}).
 */
public class ModelManager {

    private static final Logger LOG = Logger.getLogger(ModelManager.class.getName());

    private static final Path MODELS_DIR = AraPaths.modelsDir();
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofHours(6);
    private static final String USER_AGENT = "Ara/" + tech.rawden.ara.update.AppVersion.current() + " (model-download)";

    private final HttpClient httpClient;

    public ModelManager() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Path modelsDirectory() {
        return MODELS_DIR;
    }

    public void ensureModelsDirectory() throws IOException {
        Files.createDirectories(MODELS_DIR);
    }

    public Path defaultModelPath() {
        return MODELS_DIR.resolve(defaultModelFilename());
    }

    public String defaultModelFilename() {
        return ModelCatalog.resolveDefaultModel(httpClient).filename;
    }

    public String defaultModelDisplayName() {
        var model = ModelCatalog.resolveDefaultModel(httpClient);
        return model.displayName != null && !model.displayName.isBlank() ? model.displayName : model.filename;
    }

    public List<Path> listModels() throws IOException {
        if (!Files.exists(MODELS_DIR)) return List.of();
        try (Stream<Path> files = Files.list(MODELS_DIR)) {
            return files.filter(f -> f.toString().endsWith(".gguf"))
                    .filter(f -> !f.getFileName().toString().startsWith("."))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(Files.size(b), Files.size(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
        }
    }

    /** Preferred settings filename when it exists on disk; otherwise the largest .gguf. */
    public Optional<Path> resolveModel(String preferredFilename) throws IOException {
        var models = listModels();
        if (models.isEmpty()) {
            return Optional.empty();
        }
        if (preferredFilename != null && !preferredFilename.isBlank()) {
            for (var model : models) {
                if (model.getFileName().toString().equals(preferredFilename)) {
                    return Optional.of(model);
                }
            }
        }
        return Optional.of(models.getFirst());
    }

    public void deleteModel(Path path) throws IOException {
        Files.deleteIfExists(path);
        LOG.info("Deleted model: " + path);
    }

    public long modelSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.0f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public void downloadDefaultModel(ProgressCallback callback) throws IOException, InterruptedException {
        ensureModelsDirectory();
        var meta = ModelCatalog.resolveDefaultModel(httpClient);
        Path target = MODELS_DIR.resolve(meta.filename);
        Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");

        if (hasParts(meta)) {
            LOG.info("Downloading default model from Ara repo (" + meta.parts.size() + " parts)...");
            downloadAndAssembleParts(meta, tempFile, callback);
        } else if (meta.downloadUrl != null && !meta.downloadUrl.isBlank()) {
            LOG.info("Downloading default model from " + meta.downloadUrl);
            downloadUrlToFile(meta.downloadUrl, tempFile, meta.sizeBytes, callback);
        } else {
            throw new IOException("No download URL or parts defined in model metadata.");
        }

        verifyDownload(tempFile, meta);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOG.info("Download complete: " + target);
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", USER_AGENT)
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

        byte[] buf = new byte[8192];
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
