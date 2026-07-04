package tech.rawden.ara.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {

    private final String id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant createdAt;

    private final List<ChatMessage> messages;
    private String title;
    private Integer activeTeamId;
    private String teamHandoffContext = "";

    public ChatSession(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.createdAt = Instant.now();
        this.messages = new ArrayList<>();
    }

    @JsonCreator
    public ChatSession(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("activeTeamId") Integer activeTeamId,
            @JsonProperty("teamHandoffContext") String teamHandoffContext) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.messages = new ArrayList<>(messages != null ? messages : List.of());
        this.activeTeamId = activeTeamId;
        this.teamHandoffContext = teamHandoffContext != null ? teamHandoffContext : "";
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("title")
    public String title() {
        return title;
    }

    @JsonProperty("createdAt")
    public Instant createdAt() {
        return createdAt;
    }

    @JsonProperty("messages")
    public List<ChatMessage> messages() {
        return messages;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ChatMessage lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    @JsonProperty("activeTeamId")
    public Integer activeTeamId() {
        return activeTeamId;
    }

    public void setActiveTeamId(Integer activeTeamId) {
        this.activeTeamId = activeTeamId;
    }

    @JsonProperty("teamHandoffContext")
    public String teamHandoffContext() {
        return teamHandoffContext != null ? teamHandoffContext : "";
    }

    public void setTeamHandoffContext(String teamHandoffContext) {
        this.teamHandoffContext = teamHandoffContext != null ? teamHandoffContext : "";
    }
}
