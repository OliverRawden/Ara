package tech.rawden.ara.core;

import javafx.application.Platform;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * In-memory application log buffer (from JVM start) with process tags for developer diagnostics.
 * Use {@link #of(String)} for named process loggers, e.g. {@code AppLog.of("routing").info("...")}.
 */
public final class AppLog {

    private static final int MAX_ENTRIES = 50_000;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final CopyOnWriteArrayList<Entry> BUFFER = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<Entry>> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile boolean installed;
    private static volatile boolean verbose;
    private static volatile Level captureLevel = Level.INFO;

    static {
        install();
    }

    private AppLog() {}

    public record Entry(Instant time, Level level, String process, String message, String loggerName) {}

    /** Installs the root log handler (idempotent). Called automatically via static init. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        var root = Logger.getLogger("");
        var handler = new Handler() {
            {
                setLevel(Level.ALL);
            }

            @Override
            public void publish(LogRecord record) {
                if (record == null || !isLoggable(record)) {
                    return;
                }
                String message = formatMessage(record);
                if (message == null || message.isBlank()) {
                    return;
                }
                append(
                        new Entry(
                                Instant.ofEpochMilli(record.getMillis()),
                                record.getLevel(),
                                resolveProcess(record.getLoggerName()),
                                message,
                                record.getLoggerName()));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        root.addHandler(handler);
        root.setLevel(Level.INFO);
        Logger.getLogger("tech.rawden.ara").setLevel(Level.INFO);
    }

    /** Returns a process-tagged logger. Logger name is {@code ara.<process>}. */
    public static Logger of(String process) {
        if (process == null || process.isBlank()) {
            return Logger.getLogger("ara.app");
        }
        return Logger.getLogger("ara." + process.trim());
    }

    public static boolean isVerbose() {
        return verbose;
    }

    /** Enables FINE-level capture for {@code tech.rawden.ara} loggers (developer mode). */
    public static void setVerbose(boolean enabled) {
        verbose = enabled;
        captureLevel = enabled ? Level.FINE : Level.INFO;
        Level packageLevel = enabled ? Level.FINE : Level.INFO;
        Logger.getLogger("tech.rawden.ara").setLevel(packageLevel);
        for (var name : List.of("ara.routing", "ara.inference", "ara.model", "ara.chat", "ara.startup")) {
            Logger.getLogger(name).setLevel(packageLevel);
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(BUFFER);
    }

    public static void addListener(Consumer<Entry> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Consumer<Entry> listener) {
        LISTENERS.remove(listener);
    }

    public static String format(Entry entry) {
        return TIME.format(entry.time())
                + " ["
                + padProcess(entry.process())
                + "] "
                + padLevel(entry.level())
                + " "
                + entry.message();
    }

    public static String formatAll(String processFilter, Level minLevel) {
        var sb = new StringBuilder(BUFFER.size() * 96);
        for (var entry : BUFFER) {
            if (!matches(entry, processFilter, minLevel)) {
                continue;
            }
            sb.append(format(entry)).append('\n');
        }
        return sb.toString();
    }

    public static boolean matches(Entry entry, String processFilter, Level minLevel) {
        if (entry.level().intValue() < minLevel.intValue()) {
            return false;
        }
        if (processFilter == null || processFilter.isBlank() || "all".equalsIgnoreCase(processFilter)) {
            return true;
        }
        return entry.process().equalsIgnoreCase(processFilter);
    }

    public static List<String> knownProcesses() {
        var processes = new ArrayList<String>();
        processes.add("all");
        BUFFER.stream().map(Entry::process).distinct().sorted().forEach(processes::add);
        if (!processes.contains("startup")) {
            processes.add("startup");
        }
        if (!processes.contains("routing")) {
            processes.add("routing");
        }
        if (!processes.contains("inference")) {
            processes.add("inference");
        }
        if (!processes.contains("model")) {
            processes.add("model");
        }
        if (!processes.contains("chat")) {
            processes.add("chat");
        }
        return processes;
    }

    private static void append(Entry entry) {
        if (entry.level().intValue() < captureLevel.intValue()) {
            return;
        }
        BUFFER.add(entry);
        while (BUFFER.size() > MAX_ENTRIES) {
            if (!BUFFER.isEmpty()) {
                BUFFER.remove(0);
            }
        }
        for (var listener : LISTENERS) {
            try {
                if (Platform.isFxApplicationThread()) {
                    listener.accept(entry);
                } else {
                    try {
                        Platform.runLater(() -> listener.accept(entry));
                    } catch (IllegalStateException ignored) {
                        // JavaFX toolkit not ready yet — entry remains in buffer for later refresh
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String resolveProcess(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "app";
        }
        if (loggerName.startsWith("ara.")) {
            String rest = loggerName.substring(4);
            int dot = rest.indexOf('.');
            return dot > 0 ? rest.substring(0, dot) : rest;
        }
        if (loggerName.startsWith("tech.rawden.ara.")) {
            String rest = loggerName.substring("tech.rawden.ara.".length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                return rest.substring(0, dot);
            }
            return rest.isBlank() ? "app" : rest;
        }
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot >= 0 ? loggerName.substring(lastDot + 1) : loggerName;
    }

    private static String formatMessage(LogRecord record) {
        String msg = record.getMessage();
        if (msg == null) {
            return "";
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            try {
                return String.format(msg, params);
            } catch (Exception ignored) {
            }
        }
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            return msg + " — " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
        }
        return msg;
    }

    private static String padProcess(String process) {
        String p = process != null ? process : "app";
        return p.length() >= 10 ? p.substring(0, 10) : String.format("%-10s", p);
    }

    private static String padLevel(Level level) {
        String name = level != null ? level.getName() : "INFO";
        if (name.length() > 7) {
            name = name.substring(0, 7);
        }
        return String.format("%-7s", name);
    }
}