package tech.rawden.ara.ai;

import tech.rawden.ara.core.AraPaths;

import java.io.IOException;
import java.util.Optional;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Discovers, downloads, and resolves GGUF model files under {@code ~/Documents/Ara/models/}.
 * {@link #resolveModel(String)} picks the user-selected filename when present, otherwise the
 * largest local .gguf (typical default: Qwen2.5-7B Q4_K_M).
 */
public class ModelManager {

    private static final Logger LOG = Logger.getLogger(ModelManager.class.getName());

    private static final Path MODELS_DIR = AraPaths.modelsDir();
    private static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf";
    private static final String DEFAULT_MODEL_FILENAME = "Qwen2.5-7B-Instruct-Q4_K_M.gguf";

    private final HttpClient httpClient;

    public ModelManager() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Path modelsDirectory() {
        return MODELS_DIR;
    }

    public void ensureModelsDirectory() throws IOException {
        Files.createDirectories(MODELS_DIR);
    }

    public Path defaultModelPath() {
        return MODELS_DIR.resolve(DEFAULT_MODEL_FILENAME);
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
        Path target = defaultModelPath();
        LOG.info("Downloading default model from " + DEFAULT_MODEL_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEFAULT_MODEL_URL))
                .GET()
                .build();

        Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            long downloaded = 0;
            byte[] buf = new byte[8192];
            try (InputStream in = response.body();
                    var out = Files.newOutputStream(tempFile)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    downloaded += read;
                    if (callback != null) {
                        callback.onProgress(downloaded, total);
                    }
                }
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        LOG.info("Download complete: " + target);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long bytesDownloaded, long totalBytes);
    }
}
