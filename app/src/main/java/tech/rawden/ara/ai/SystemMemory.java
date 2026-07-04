package tech.rawden.ara.ai;

/** Reads unified memory stats from the JVM (Apple Silicon shares RAM with Metal). */
public final class SystemMemory {

    private SystemMemory() {}

    public static long totalBytes() {
        try {
            var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize();
            }
        } catch (Exception ignored) {
        }
        return 16L * 1024 * 1024 * 1024;
    }

    public static long freeBytes() {
        try {
            var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getFreeMemorySize();
            }
        } catch (Exception ignored) {
        }
        return 4L * 1024 * 1024 * 1024;
    }
}