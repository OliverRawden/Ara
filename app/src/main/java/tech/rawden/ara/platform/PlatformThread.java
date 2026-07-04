package tech.rawden.ara.platform;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;

/**
 * JavaFX thread marshalling helpers.
 *
 * <p><b>Threading contract:</b> all UI mutations must run on the JavaFX application thread. Background
 * work (inference, disk I/O, HTTP) runs on virtual threads; use {@link #runLaterIfNeeded(Runnable)} to
 * hop back to FX without blocking the caller.
 *
 * <p><b>Thread-safety:</b> methods are safe to call from any thread.
 */
public class PlatformThread {

    /** Runs on the FX thread immediately if already there; otherwise schedules via {@code Platform.runLater}. */
    public static void runLaterIfNeeded(Runnable r) {
        Runnable catcher = () -> {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        if (Platform.isFxApplicationThread()) {
            catcher.run();
        } else {
            Platform.runLater(catcher);
        }
    }

    public static void runLaterIfNeededBlocking(Runnable r) {
        Runnable catcher = () -> {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        if (!Platform.isFxApplicationThread()) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                catcher.run();
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException ignored) {
            }
        } else {
            catcher.run();
        }
    }
}
