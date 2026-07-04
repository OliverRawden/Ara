package tech.rawden.ara.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChatHistory {

    private final List<ChatSession> sessions;
    private String activeSessionId;

    public ChatHistory() {
        this.sessions = new ArrayList<>();
    }

    @JsonCreator
    public ChatHistory(
            @JsonProperty("sessions") List<ChatSession> sessions,
            @JsonProperty("activeSessionId") String activeSessionId) {
        this.sessions = new ArrayList<>(sessions != null ? sessions : List.of());
        this.activeSessionId = activeSessionId;
    }

    @JsonProperty("sessions")
    public List<ChatSession> sessions() {
        return List.copyOf(sessions);
    }

    @JsonProperty("activeSessionId")
    public String activeSessionId() {
        return activeSessionId;
    }

    public List<ChatSession> mutableSessions() {
        return sessions;
    }

    public ChatSession createSession(String title) {
        var session = new ChatSession(title);
        sessions.add(session);
        activeSessionId = session.id();
        return session;
    }

    public Optional<ChatSession> findById(String id) {
        return sessions.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public ChatSession activeSession() {
        if (activeSessionId == null) {
            return sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
        }
        return findById(activeSessionId).orElse(sessions.isEmpty() ? null : sessions.get(sessions.size() - 1));
    }

    public void setActiveSession(ChatSession session) {
        this.activeSessionId = session.id();
    }

    public void removeSession(ChatSession session) {
        sessions.remove(session);
        if (session.id().equals(activeSessionId)) {
            activeSessionId = sessions.isEmpty()
                    ? null
                    : sessions.get(sessions.size() - 1).id();
        }
    }

    /** Removes sessions with no user messages (drafts that were never started). */
    public void purgeEmptySessions() {
        sessions.removeIf(s -> !s.hasUserMessages());
        if (activeSessionId != null && findById(activeSessionId).isEmpty()) {
            activeSessionId = sessions.isEmpty() ? null : sessions.get(sessions.size() - 1).id();
        }
    }

    /** Snapshot for persistence — only conversations the user actually started. */
    public ChatHistory persistableSnapshot() {
        var saved = sessions.stream().filter(ChatSession::hasUserMessages).toList();
        String activeId = activeSessionId;
        final String currentActiveId = activeId;
        if (currentActiveId != null && saved.stream().noneMatch(s -> s.id().equals(currentActiveId))) {
            activeId = saved.isEmpty() ? null : saved.get(saved.size() - 1).id();
        }
        return new ChatHistory(saved, activeId);
    }

    public void replaceWith(ChatHistory history) {
        sessions.clear();
        sessions.addAll(history.sessions());
        activeSessionId = history.activeSessionId();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return sessions.isEmpty();
    }
}
