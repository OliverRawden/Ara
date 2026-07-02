package tech.rawden.ara.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.rawden.ara.util.OsType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Optional, privacy-respecting update checks against {@code installers/latest.json}.
 * Network access only occurs when the user enables checks or taps "Check for updates now".
 */
public class UpdateService {

    private static final Logger LOG = Logger.getLogger(UpdateService.class.getName());

    /** Public raw GitHub URL for {@code installers/latest.json} on the main branch. */
    public static final String DEFAULT_METADATA_URL =
            "https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final String USER_AGENT = "Ara/" + AppVersion.current() + " (update-check)";

    private final HttpClient httpClient;
    private final String metadataUrl;

    public UpdateService() {
        this(DEFAULT_METADATA_URL);
    }

    public UpdateService(String metadataUrl) {
        this.metadataUrl = metadataUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(METADATA_TIMEOUT)
                .build();
    }

    /**
     * Fetches release metadata and returns update details when a newer stable version is available.
     * Empty when already up to date, metadata is invalid, or no platform installer URL exists.
     */
    public Optional<UpdateInfo> checkForUpdate() throws IOException, InterruptedException {
        String current = AppVersion.current();
        if ("unknown".equals(current)) {
            LOG.warning("Skipping update check — current version is unknown.");
            return Optional.empty();
        }

        ReleaseMetadata metadata = fetchMetadata();
        if (metadata.latestVersion == null || metadata.latestVersion.isBlank()) {
            LOG.warning("Update metadata missing latestVersion.");
            return Optional.empty();
        }

        String remote = VersionComparer.normalize(metadata.latestVersion);
        if (!VersionComparer.isNewer(remote, current)) {
            LOG.info("No update available (current=" + current + ", latest=" + remote + ").");
            return Optional.empty();
        }

        Optional<String> downloadUrl = PlatformInstallerKey.resolveDownloadUrl(metadata.downloads);
        if (downloadUrl.isEmpty()) {
            LOG.warning("Update " + remote + " found but no installer URL for this platform.");
            return Optional.empty();
        }

        return Optional.of(new UpdateInfo(
                remote,
                metadata.releaseDate,
                metadata.releaseNotes != null ? metadata.releaseNotes : "",
                downloadUrl.get(),
                current));
    }

    /**
     * Downloads the installer to a temp file and opens it with the desktop handler (Finder, Explorer, etc.).
     * The user completes installation through the native installer UI.
     */
    public Path downloadAndLaunchInstaller(String url) throws IOException, InterruptedException {
        return downloadAndLaunchInstaller(url, null);
    }

    public Path downloadAndLaunchInstaller(String url, DownloadProgress progress)
            throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String fileName = extractFileName(uri);
        Path tempDir = Files.createTempDirectory("ara-update-");
        Path target = tempDir.resolve(fileName);

        LOG.info("Downloading update installer from " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/octet-stream")
                .GET()
                .timeout(DOWNLOAD_TIMEOUT)
                .build();

        Path tempFile = target.resolveSibling(fileName + ".part");
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Download failed with HTTP " + response.statusCode());
            }

            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            long downloaded = 0;
            byte[] buffer = new byte[8192];

            try (InputStream in = response.body();
                    var out = Files.newOutputStream(tempFile)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (progress != null) {
                        progress.onProgress(downloaded, total);
                    }
                }
            }

            Files.move(tempFile, target);
            launchInstaller(target);
            return target;
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private ReleaseMetadata fetchMetadata() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(METADATA_TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Could not fetch update metadata (HTTP " + response.statusCode() + ")");
        }

        ReleaseMetadata metadata = MAPPER.readValue(response.body(), ReleaseMetadata.class);
        LOG.fine("Fetched update metadata: latest=" + metadata.latestVersion);
        return metadata;
    }

    private static void launchInstaller(Path file) throws IOException {
        ProcessBuilder builder = switch (OsType.ofLocal()) {
            case OsType.MacOs mac -> new ProcessBuilder("open", file.toString());
            case OsType.Windows windows -> new ProcessBuilder("cmd", "/c", "start", "", file.toString());
            case OsType.Linux linux -> new ProcessBuilder("xdg-open", file.toString());
        };
        LOG.info("Launching installer: " + file);
        builder.start();
    }

    private static String extractFileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return "Ara-update-installer";
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? "Ara-update-installer" : name;
    }

    @FunctionalInterface
    public interface DownloadProgress {
        void onProgress(long bytesDownloaded, long totalBytes);
    }
}