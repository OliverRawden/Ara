package tech.rawden.ara.ai;

import tech.rawden.ara.core.AraPaths;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    private final HttpClient httpClient;
    private final ModelDownloader downloader;

    public ModelManager() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.downloader = new ModelDownloader(httpClient);
    }

    public ModelManager(int downloadChunkSizeMb) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.downloader = new ModelDownloader(httpClient, downloadChunkSizeMb);
    }

    public Path modelsDirectory() {
        return MODELS_DIR;
    }

    public void ensureModelsDirectory() throws IOException {
        downloader.ensureModelsDirectory();
    }

    public Path defaultModelPath() {
        return MODELS_DIR.resolve(defaultModelFilename());
    }

    public Path heavyModelPath(String preferredFilename) {
        String filename = preferredFilename != null && !preferredFilename.isBlank()
                ? preferredFilename
                : heavyModelFilename();
        return MODELS_DIR.resolve(filename);
    }

    public String defaultModelFilename() {
        return ModelCatalog.resolveDefaultModel(httpClient).filename;
    }

    public String heavyModelFilename() {
        return ModelCatalog.resolveHeavyModel(httpClient).filename;
    }

    public String defaultModelDisplayName() {
        var model = ModelCatalog.resolveDefaultModel(httpClient);
        return model.displayName != null && !model.displayName.isBlank() ? model.displayName : model.filename;
    }

    public String heavyModelDisplayName() {
        var model = ModelCatalog.resolveHeavyModel(httpClient);
        return model.displayName != null && !model.displayName.isBlank() ? model.displayName : model.filename;
    }

    public boolean isHeavyDownloadAvailable() {
        return downloader.isDownloadAvailable(ModelCatalog.resolveHeavyModel(httpClient));
    }

    public boolean isModelPresent(String filename) throws IOException {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        Path path = MODELS_DIR.resolve(filename);
        return Files.exists(path) && Files.size(path) > 0;
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
        downloadModel(ModelCatalog.resolveDefaultModel(httpClient), callback);
    }

    public void downloadHeavyModel(ProgressCallback callback) throws IOException, InterruptedException {
        var meta = ModelCatalog.resolveHeavyModel(httpClient);
        if (!downloader.isDownloadAvailable(meta)) {
            throw new IOException(
                    "Heavy model is not yet hosted on the Ara repo. Place a GGUF in "
                            + MODELS_DIR
                            + " or update installers/models.json with download parts.");
        }
        downloadModel(meta, callback);
    }

    private void downloadModel(ModelRelease.DefaultModel meta, ProgressCallback callback)
            throws IOException, InterruptedException {
        downloader.download(
                meta,
                callback != null
                        ? (downloaded, total) -> callback.onProgress(downloaded, total)
                        : null);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long bytesDownloaded, long totalBytes);
    }
}