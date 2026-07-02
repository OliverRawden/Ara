package tech.rawden.ara.tool;

/**
 * Strips {@code <|tool_call|>} syntax from assistant text before it is shown in chat bubbles.
 * The raw content may still be stored for the agent loop; this is display-only.
 */
public final class ToolCallDisplay {

    private static final String MARKER = "<|tool_call|>";
    private static final String PARTIAL_PREFIX = "<|tool";

    private ToolCallDisplay() {}

    /** Text safe to render in the UI — hides tool-call markers and any trailing partial marker. */
    public static String forDisplay(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int markerIdx = text.indexOf(MARKER);
        if (markerIdx >= 0) {
            text = text.substring(0, markerIdx);
        }
        int partialIdx = text.indexOf(PARTIAL_PREFIX);
        if (partialIdx >= 0) {
            text = text.substring(0, partialIdx);
        }
        return text.stripTrailing();
    }
}