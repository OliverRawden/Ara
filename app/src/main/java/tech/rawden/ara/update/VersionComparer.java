package tech.rawden.ara.update;

/**
 * Lightweight semantic version comparison for dotted numeric versions (e.g. {@code 5.6}, {@code 5.6.1}).
 * Strips {@code -SNAPSHOT} and similar build suffixes before comparing.
 */
public final class VersionComparer {

    private VersionComparer() {}

    /** Returns true when {@code remote} is strictly newer than {@code local}. */
    public static boolean isNewer(String remote, String local) {
        return compare(remote, local) > 0;
    }

    /** Negative if a &lt; b, zero if equal, positive if a &gt; b. */
    public static int compare(String a, String b) {
        int[] partsA = parse(normalize(a));
        int[] partsB = parse(normalize(b));
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int va = i < partsA.length ? partsA[i] : 0;
            int vb = i < partsB.length ? partsB[i] : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    /** Strips build qualifiers such as {@code -SNAPSHOT} for stable comparison. */
    public static String normalize(String version) {
        if (version == null || version.isBlank()) {
            return "0";
        }
        String trimmed = version.strip();
        int dash = trimmed.indexOf('-');
        return dash >= 0 ? trimmed.substring(0, dash).strip() : trimmed;
    }

    private static int[] parse(String version) {
        String[] tokens = version.split("\\.");
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].replaceAll("[^0-9].*$", "");
            parts[i] = token.isEmpty() ? 0 : Integer.parseInt(token);
        }
        return parts;
    }
}
