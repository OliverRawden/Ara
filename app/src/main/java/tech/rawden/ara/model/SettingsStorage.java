package tech.rawden.ara.model;

import tech.rawden.ara.core.AraPaths;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class SettingsStorage {

    private static final Logger LOG = Logger.getLogger(SettingsStorage.class.getName());

    private static final Path DATA_DIR = AraPaths.dataDir();
    private static final Path SETTINGS_FILE = DATA_DIR.resolve("settings.json");

    // Static shared mapper for faster repeated use and lower startup overhead
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
    }

    public SettingsStorage() {
        // mapper is static
    }

    public void save(AppSettings settings) {
        try {
            Files.createDirectories(DATA_DIR);
            MAPPER.writeValue(SETTINGS_FILE.toFile(), settings);
            LOG.fine("Settings saved to " + SETTINGS_FILE);
        } catch (IOException e) {
            LOG.warning("Failed to save settings: " + e.getMessage());
        }
    }

    public AppSettings load() {
        if (!Files.exists(SETTINGS_FILE)) {
            LOG.info("No saved settings found at " + SETTINGS_FILE);
            return new AppSettings();
        }
        try {
            return MAPPER.readValue(SETTINGS_FILE.toFile(), AppSettings.class);
        } catch (IOException e) {
            LOG.warning("Failed to load settings: " + e.getMessage());
            return new AppSettings();
        }
    }

    public boolean delete() {
        try {
            tech.rawden.ara.core.SecurityService.secureDelete(SETTINGS_FILE);
            return true;
        } catch (Exception e) {
            LOG.warning("Failed to securely delete settings: " + e.getMessage());
            return false;
        }
    }

    public Path settingsFile() {
        return SETTINGS_FILE;
    }
}
