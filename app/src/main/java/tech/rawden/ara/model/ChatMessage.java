package tech.rawden.ara.model;

import java.time.Instant;

public record ChatMessage(String id, String sessionId, Role role, String content, Instant timestamp) {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    public static ChatMessage userMessage(String sessionId, String content) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(), sessionId, Role.USER, content, Instant.now());
    }

    public static ChatMessage assistantMessage(String sessionId, String content) {
        return new ChatMessage(
                java.util.UUID.randomUUID().toString(), sessionId, Role.ASSISTANT, content, Instant.now());
    }

    public static ChatMessage toolMessage(String sessionId, String command) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(), sessionId, Role.TOOL, command, Instant.now());
    }
}
