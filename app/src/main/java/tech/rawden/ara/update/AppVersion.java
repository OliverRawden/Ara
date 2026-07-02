package tech.rawden.ara.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Resolves the running app version. Packaged builds receive {@code -Dtech.rawden.ara.version=…}
 * from jpackage; development builds fall back to the root {@code version} file copied into resources
 * at build time.
 */
public final class AppVersion {

    private static final Logger LOG = Logger.getLogger(AppVersion.class.getName());
    private static final String VERSION_PROPERTY = "tech.rawden.ara.version";
    private static final String VERSION_RESOURCE = "/tech/rawden/ara/resources/version";

    private static final String CURRENT = resolve();

    private AppVersion() {}

    /** Canonical version string for display and update comparison (snapshot suffix stripped). */
    public static String current() {
        return CURRENT;
    }

    private static String resolve() {
        String fromJvm = System.getProperty(VERSION_PROPERTY);
        if (fromJvm != null && !fromJvm.isBlank()) {
            return VersionComparer.normalize(fromJvm);
        }

        try (InputStream in = AppVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                if (!text.isEmpty()) {
                    return VersionComparer.normalize(text);
                }
            }
        } catch (IOException e) {
            LOG.fine("Could not read bundled version resource: " + e.getMessage());
        }

        LOG.warning("App version unknown — update checks may be unreliable.");
        return "unknown";
    }
}