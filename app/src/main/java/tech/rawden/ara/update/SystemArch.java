package tech.rawden.ara.update;

/** Normalizes {@code os.arch} to the suffix used in installer filenames ({@code arm64}, {@code x86_64}). */
public final class SystemArch {

    private SystemArch() {}

    public static String normalize() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }
        return arch.isBlank() ? "unknown" : arch;
    }
}
