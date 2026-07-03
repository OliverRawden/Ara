package tech.rawden.ara.ai;

import tech.rawden.ara.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** Trims chat history to a character budget before inference (prefill cost grows quadratically). */
public final class PromptContextLimiter {

    public static final int DEFAULT_MAX_CHARS = 28_000;
    public static final int MIN_RECENT_MESSAGES = 4;

    private PromptContextLimiter() {}

    public record Result(List<ChatMessage> history, int droppedMessages) {}

    public static Result limit(List<ChatMessage> history, int maxChars) {
        if (history == null || history.isEmpty() || maxChars <= 0) {
            return new Result(history != null ? history : List.of(), 0);
        }

        int total = history.stream().mapToInt(m -> messageChars(m)).sum();
        if (total <= maxChars) {
            return new Result(history, 0);
        }

        var kept = new ArrayList<>(history);
        int dropped = 0;
        while (kept.size() > MIN_RECENT_MESSAGES && total > maxChars) {
            kept.remove(0);
            dropped++;
            total = kept.stream().mapToInt(PromptContextLimiter::messageChars).sum();
        }
        return new Result(List.copyOf(kept), dropped);
    }

    private static int messageChars(ChatMessage message) {
        var content = message.content();
        return content != null ? content.length() + 32 : 32;
    }
}
