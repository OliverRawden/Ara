package tech.rawden.ara.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadHelper {

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public static void runAsync(Runnable r) {
        executor.execute(r);
    }

    public static ExecutorService executor() {
        return executor;
    }
}
