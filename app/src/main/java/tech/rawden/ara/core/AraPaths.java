package tech.rawden.ara.core;

import java.nio.file.Path;

/** Canonical runtime paths under {@code ~/Documents/Ara/} and the Vex protocols directory. */
public class AraPaths {

    private AraPaths() {}

    private static final Path BASE = Path.of(System.getProperty("user.home"), "Documents", "Ara");
    private static final Path DATA_DIR = BASE.resolve("data");
    private static final Path MODELS_DIR = BASE.resolve("models");
    private static final Path CONTEXT_FILE = BASE.resolve("context.md");
    private static final Path LOGS_DIR = BASE.resolve("logs");
    private static final Path AUDIT_LOG_FILE = LOGS_DIR.resolve("audit.log");
    private static final Path VEX_PROTOCOLS_DIR =
            Path.of(System.getProperty("user.home"), "Documents", "Vex", "Protocols");

    public static Path base() {
        return BASE;
    }

    public static Path dataDir() {
        return DATA_DIR;
    }

    public static Path modelsDir() {
        return MODELS_DIR;
    }

    public static Path contextFile() {
        return CONTEXT_FILE;
    }

    public static Path logsDir() {
        return LOGS_DIR;
    }

    public static Path auditLogFile() {
        return AUDIT_LOG_FILE;
    }

    /** Shared protocol definitions managed by Vex; Ara reads ara-tool entries at runtime. */
    public static Path vexProtocolsDir() {
        return VEX_PROTOCOLS_DIR;
    }
}
