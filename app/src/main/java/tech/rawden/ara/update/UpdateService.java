package tech.rawden.ara.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.rawden.ara.model.AppSettings;
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
 * For private GitHub repositories, pass a personal access token via {@link AppSettings}.
 */
public class UpdateService {

    private static final Logger LOG = Logger.getLogger(UpdateService.class.getName());

    /** Raw GitHub URL for {@code installers/latest.json} on the main branch. */
    public static final String DEFAULT_METADATA_URL =
            "https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final String USER_AGENT = "Ara/" + AppVersion.current() + " (update-check)";

    private final HttpClient httpClient;
    private final String metadataUrl;
    private final String githubAccessToken;

    public UpdateService() {
        this(DEFAULT_METADATA_URL, null);
    }

    public static UpdateService forSettings(AppSettings settings) {
        if (settings == null) {
            return new UpdateService();
        }
        return new UpdateService(DEFAULT_METADATA_URL, settings.getGithubAccessToken());
    }

    public UpdateService(String metadataUrl, String githubAccessToken) {
        this.metadataUrl = metadataUrl;
        this.githubAccessToken = normalizeToken(githubAccessToken);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(METADATA_TIMEOUT)
                .build();
    }

    public boolean usesPrivateRepoAuth() {
        return githubAccessToken != null;
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

        HttpRequest request = authorize(HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/octet-stream")
                        .GET()
                        .timeout(DOWNLOAD_TIMEOUT))
                .build();

        Path tempFile = target.resolveSibling(fileName + ".part");
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw downloadFailure(response.statusCode());
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
        HttpRequest request = authorize(HttpRequest.newBuilder()
                        .uri(URI.create(metadataUrl))
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .timeout(METADATA_TIMEOUT))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw metadataFailure(response.statusCode());
        }

        ReleaseMetadata metadata = MAPPER.readValue(response.body(), ReleaseMetadata.class);
        LOG.fine("Fetched update metadata: latest=" + metadata.latestVersion);
        return metadata;
    }

    private HttpRequest.Builder authorize(HttpRequest.Builder builder) {
        if (githubAccessToken != null) {
            builder.header("Authorization", "Bearer " + githubAccessToken);
        }
        return builder;
    }

    private IOException metadataFailure(int status) {
        String hint = authHint(status);
        return new IOException("Could not fetch update metadata (HTTP " + status + ")." + hint);
    }

    private IOException downloadFailure(int status) {
        String hint = authHint(status);
        return new IOException("Download failed with HTTP " + status + "." + hint);
    }

    private String authHint(int status) {
        if (status != 401 && status != 403 && status != 404) {
            return "";
        }
        if (githubAccessToken != null) {
            return " Check that your GitHub token has the 'repo' scope and access to OliverRawden/Ara.";
        }
        return " This repository may be private — add a GitHub personal access token in Settings → Updates.";
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.strip();
        return trimmed.isEmpty() ? null : trimmed;
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