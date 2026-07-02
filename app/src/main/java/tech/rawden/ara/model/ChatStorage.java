package tech.rawden.ara.model;

import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.SecurityService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Persists {@link ChatHistory} to {@code ~/Documents/Ara/data/chats.json} with optional AES-GCM. */
public class ChatStorage {

    private static final Logger LOG = Logger.getLogger(ChatStorage.class.getName());

    private static final Path DATA_DIR = AraPaths.dataDir();
    private static final Path CHATS_FILE = DATA_DIR.resolve("chats.json");

    // Reuse single ObjectMapper for faster startup and lower memory (expensive to construct)
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ChatStorage() {
        // no per-instance mapper
    }

    public void save(ChatHistory history) {
        try {
            Files.createDirectories(DATA_DIR);
            byte[] jsonBytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(history);

            byte[] toWrite = SecurityService.isEncryptionEnabled() ? SecurityService.encrypt(jsonBytes) : jsonBytes;

            Files.write(CHATS_FILE, toWrite);
            LOG.fine("Chats saved to " + CHATS_FILE + (SecurityService.isEncryptionEnabled() ? " (encrypted)" : ""));
        } catch (IOException e) {
            LOG.warning("Failed to save chats: " + e.getMessage());
        }
    }

    public ChatHistory load() {
        if (!Files.exists(CHATS_FILE)) {
            LOG.info("No saved chats found at " + CHATS_FILE);
            return new ChatHistory();
        }
        try {
            byte[] fileBytes = Files.readAllBytes(CHATS_FILE);
            byte[] jsonBytes = SecurityService.isEncryptionEnabled() ? SecurityService.decrypt(fileBytes) : fileBytes;

            var history = MAPPER.readValue(jsonBytes, ChatHistory.class);
            LOG.info("Loaded " + history.mutableSessions().size() + " chat sessions"
                    + (SecurityService.isEncryptionEnabled() ? " (decrypted)" : ""));
            return history;
        } catch (Exception e) {
            LOG.warning("Failed to load chats: " + e.getMessage());
            return new ChatHistory();
        }
    }

    public boolean delete() {
        try {
            tech.rawden.ara.core.SecurityService.secureDelete(CHATS_FILE);
            return true;
        } catch (Exception e) {
            LOG.warning("Failed to securely delete chats: " + e.getMessage());
            return false;
        }
    }

    public Path chatsFile() {
        return CHATS_FILE;
    }
}
