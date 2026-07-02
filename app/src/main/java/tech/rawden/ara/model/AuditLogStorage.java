package tech.rawden.ara.model;

import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.SecurityService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Persistent storage for the privacy/security audit log.
 * Respects the global encryption setting via SecurityService.
 */
public class AuditLogStorage {

    private static final Logger LOG = Logger.getLogger(AuditLogStorage.class.getName());

    private static final Path LOGS_DIR = AraPaths.logsDir();
    private static final Path AUDIT_FILE = AraPaths.auditLogFile();

    // Shared static mapper (Jackson construction is expensive; reuse across the app)
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public AuditLogStorage() {
        // static
    }

    public void save(AuditLog log) {
        try {
            Files.createDirectories(LOGS_DIR);
            byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(log);

            byte[] toWrite = SecurityService.isEncryptionEnabled() ? SecurityService.encrypt(json) : json;

            Files.write(AUDIT_FILE, toWrite);
            LOG.fine("Audit log saved" + (SecurityService.isEncryptionEnabled() ? " (encrypted)" : ""));
        } catch (IOException e) {
            LOG.warning("Failed to save audit log: " + e.getMessage());
        }
    }

    public AuditLog load() {
        if (!Files.exists(AUDIT_FILE)) {
            return new AuditLog();
        }
        try {
            byte[] fileBytes = Files.readAllBytes(AUDIT_FILE);
            byte[] jsonBytes = SecurityService.isEncryptionEnabled() ? SecurityService.decrypt(fileBytes) : fileBytes;

            var log = MAPPER.readValue(jsonBytes, AuditLog.class);
            return log;
        } catch (Exception e) {
            LOG.warning("Failed to load audit log (may be wrong passphrase): " + e.getMessage());
            return new AuditLog();
        }
    }

    public boolean delete() {
        try {
            tech.rawden.ara.core.SecurityService.secureDelete(AUDIT_FILE);
            return true;
        } catch (Exception e) {
            LOG.warning("Failed to securely delete audit log: " + e.getMessage());
            return false;
        }
    }

    public Path auditLogFile() {
        return AUDIT_FILE;
    }
}
