package tech.rawden.ara.update;

/**
 * Describes an available update returned by {@link UpdateService#checkForUpdate()}.
 *
 * @param latestVersion version advertised in release metadata
 * @param releaseDate   ISO date string from metadata (may be null)
 * @param releaseNotes  human-readable changelog text
 * @param downloadUrl   platform-appropriate installer URL
 * @param currentVersion running app version at check time
 */
public record UpdateInfo(
        String latestVersion,
        String releaseDate,
        String releaseNotes,
        String downloadUrl,
        String currentVersion) {}