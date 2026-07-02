package tech.rawden.ara.model;

import java.time.Instant;

/**
 * Represents a single auditable security/privacy-relevant event.
 */
public class AuditLogEntry {

    private Instant timestamp;
    private String eventType; // e.g. APP_START, MODEL_LOAD, TOOL_CALL, CONTEXT_ACCESS, SETTINGS_CHANGE, DATA_CLEAR
    private String details; // Human readable. For tool calls, include command or query (be careful with secrets).
    private String sessionId; // Optional chat session
    private int riskLevel; // 0=info, 1=low, 2=medium, 3=high (terminal exec, data deletion, etc.)

    public AuditLogEntry() {
        // for Jackson
    }

    public AuditLogEntry(String eventType, String details, String sessionId, int riskLevel) {
        this.timestamp = Instant.now();
        this.eventType = eventType;
        this.details = details;
        this.sessionId = sessionId;
        this.riskLevel = Math.max(0, Math.min(3, riskLevel));
    }

    // Getters and setters for Jackson + UI
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(int riskLevel) {
        this.riskLevel = riskLevel;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (risk=%d): %s", timestamp, eventType, riskLevel, details);
    }
}
