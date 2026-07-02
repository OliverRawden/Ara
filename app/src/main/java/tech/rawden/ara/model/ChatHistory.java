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
