package tech.rawden.ara.model;

import tech.rawden.ara.core.AraConfig;
import tech.rawden.ara.core.AppLog;

import java.nio.file.Files;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Hot-reloads {@link AppSettings} from disk when developer mode is enabled.
 *
 * <p>Polls {@link SettingsStorage#settingsFile()} mtime on a virtual thread and notifies listeners
 * when the file changes. Intended for live tuning during development without restarting Ara.
 *
 * <p><b>Thread-safety:</b> listeners run on the poller virtual thread; UI updates must use
 * {@link javafx.application.Platform#runLater(Runnable)}.
 */
public final class SettingsReloader {

    private static final Logger LOG = AppLog.of("settings");

    private final SettingsStorage storage;
    private final CopyOnWriteArrayList<Consumer<AppSettings>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile long lastMtime;

    public SettingsReloader(SettingsStorage storage) {
        this.storage = storage;
        refreshMtime();
    }

    public void addListener(Consumer<AppSettings> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<AppSettings> listener) {
        listeners.remove(listener);
    }

    /** Starts the background poller if not already running. Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("ara-settings-reload").start(this::pollLoop);
        LOG.info("Settings hot-reload enabled (interval " + AraConfig.settingsReloadInterval().toSeconds() + "s)");
    }

    public void stop() {
        running.set(false);
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                Thread.sleep(AraConfig.settingsReloadInterval().toMillis());
                var path = storage.settingsFile();
                if (!Files.exists(path)) {
                    continue;
                }
                long mtime = Files.getLastModifiedTime(path).toMillis();
                if (mtime != lastMtime) {
                    lastMtime = mtime;
                    var settings = storage.load();
                    LOG.info("Settings reloaded from disk (developer mode)");
                    for (var listener : listeners) {
                        try {
                            listener.accept(settings);
                        } catch (Exception e) {
                            LOG.warning("Settings reload listener failed: " + e.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.fine("Settings reload poll: " + e.getMessage());
            }
        }
    }

    private void refreshMtime() {
        try {
            var path = storage.settingsFile();
            lastMtime = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
        } catch (Exception e) {
            lastMtime = 0;
        }
    }
}