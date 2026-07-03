package tech.rawden.ara.update;

import tech.rawden.ara.util.OsType;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Maps the local OS and CPU architecture to keys in {@code installers/latest.json}.
 * macOS prefers {@code macos-pkg*} (in-place update) over {@code macos-dmg*}.
 */
public final class PlatformInstallerKey {

    private static final Logger LOG = Logger.getLogger(PlatformInstallerKey.class.getName());

    private PlatformInstallerKey() {}

    /** Resolves the best installer URL for this machine from the metadata downloads map. */
    public static Optional<String> resolveDownloadUrl(Map<String, String> downloads) {
        if (downloads == null || downloads.isEmpty()) {
            return Optional.empty();
        }

        String arch = SystemArch.normalize();
        OsType os = OsType.ofLocal();
        if (os == OsType.MACOS) {
            return firstPresent(downloads, "macos-pkg-" + arch, "macos-pkg", "macos-dmg-" + arch, "macos-dmg");
        }
        if (os == OsType.WINDOWS) {
            return firstPresent(downloads, "windows-msi-" + arch, "windows-msi", "windows");
        }
        return firstPresent(downloads, "linux-deb-" + arch, "linux-deb", "linux-rpm-" + arch, "linux-rpm", "linux");
    }

    private static Optional<String> firstPresent(Map<String, String> downloads, String... keys) {
        for (String key : keys) {
            String url = downloads.get(key);
            if (url != null && !url.isBlank()) {
                return Optional.of(url.strip());
            }
        }
        LOG.fine("No installer URL for keys: " + String.join(", ", keys));
        return Optional.empty();
    }
}
