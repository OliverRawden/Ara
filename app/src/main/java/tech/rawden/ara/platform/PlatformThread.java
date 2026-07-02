package tech.rawden.ara.platform;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;

public class PlatformThread {

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
