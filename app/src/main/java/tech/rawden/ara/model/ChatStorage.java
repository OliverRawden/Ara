package tech.rawden.ara.model;

import tech.rawden.ara.core.AraConfig;
import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.SecurityService;
import tech.rawden.ara.util.AraFailures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Persists {@link ChatHistory} to {@code ~/Documents/Ara/data/chats.json} with optional AES-GCM.
 *
 * <p><b>Lazy loading:</b> {@link #load()} and {@link #load(int)} cap the number of sessions
 * deserialized at startup ({@link AraConfig#chatLoadSessionLimit()}) so large histories do not
 * block the UI. Older sessions remain on disk; a future open-by-id path can load them on demand.
 *
 * <p><b>Thread-safety:</b> each instance is safe for concurrent reads from virtual threads; callers
 * should not write concurrently without external synchronization.
 *
 * @see tech.rawden.ara.Main#start — loads history on {@code ara-data-loader} virtual thread
 */
public class ChatStorage {

    private static final Logger LOG = Logger.getLogger(ChatStorage.class.getName());

    private static final Path DATA_DIR = AraPaths.dataDir();
    private static final Path CHATS_FILE = DATA_DIR.resolve("chats.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ChatStorage() {}

    /**
     * Loads chat history with the default session cap from {@link AraConfig#chatLoadSessionLimit()}.
     */
    public ChatHistory load() {
        return load(AraConfig.chatLoadSessionLimit());
    }

    /**
     * Loads chat history, deserializing at most {@code maxSessions} most-recent sessions.
     *
     * @param maxSessions maximum sessions to load; {@code <= 0} loads all sessions
     */
    public ChatHistory load(int maxSessions) {
        if (!Files.exists(CHATS_FILE)) {
            LOG.info("No saved chats found at " + CHATS_FILE);
            return new ChatHistory();
        }
        try {
            byte[] fileBytes = Files.readAllBytes(CHATS_FILE);
            byte[] jsonBytes = SecurityService.isEncryptionEnabled() ? SecurityService.decrypt(fileBytes) : fileBytes;

            JsonNode root = MAPPER.readTree(jsonBytes);
            JsonNode sessionsNode = root.get("sessions");
            String activeId = root.hasNonNull("activeSessionId") ? root.get("activeSessionId").asText() : null;

            List<ChatSession> sessions = new ArrayList<>();
            if (sessionsNode != null && sessionsNode.isArray()) {
                int total = sessionsNode.size();
                int start = maxSessions > 0 && total > maxSessions ? total - maxSessions : 0;
                if (start > 0) {
                    LOG.info("Lazy-loading " + (total - start) + " of " + total + " sessions (cap=" + maxSessions + ")");
                }
                for (int i = start; i < total; i++) {
                    sessions.add(MAPPER.treeToValue(sessionsNode.get(i), ChatSession.class));
                }
            }

            var history = new ChatHistory(sessions, activeId);
            history.purgeEmptySessions();
            LOG.info("Loaded " + history.mutableSessions().size() + " chat sessions"
                    + (SecurityService.isEncryptionEnabled() ? " (decrypted)" : ""));
            return history;
        } catch (Exception e) {
            LOG.warning("Failed to load chats: " + e.getMessage());
            return new ChatHistory();
        }
    }

    /**
     * Asynchronously loads chat history on the given executor (typically a virtual thread).
     */
    public CompletableFuture<ChatHistory> loadAsync(Executor executor) {
        return CompletableFuture.supplyAsync(this::load, executor);
    }

    public void save(ChatHistory history) {
        try {
            Files.createDirectories(DATA_DIR);
            byte[] jsonBytes =
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(history.persistableSnapshot());

            byte[] toWrite = SecurityService.isEncryptionEnabled() ? SecurityService.encrypt(jsonBytes) : jsonBytes;

            Files.write(CHATS_FILE, toWrite);
            LOG.fine("Chats saved to " + CHATS_FILE + (SecurityService.isEncryptionEnabled() ? " (encrypted)" : ""));
        } catch (IOException e) {
            LOG.warning("Failed to save chats: " + AraFailures.chatPersistence("save", e).getMessage());
        }
    }

    public boolean delete() {
        try {
            SecurityService.secureDelete(CHATS_FILE);
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