package tech.rawden.ara.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for audit log entries. Kept small in memory; persisted via AuditLogStorage.
 */
public class AuditLog {

    private List<AuditLogEntry> entries = new ArrayList<>();

    public List<AuditLogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<AuditLogEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public void addEntry(AuditLogEntry entry) {
        if (entry != null) {
            entries.add(entry);
            // Keep memory bounded for very long-running sessions
            if (entries.size() > 5000) {
                entries = new ArrayList<>(entries.subList(entries.size() - 2000, entries.size()));
            }
        }
    }

    public void clear() {
        entries.clear();
    }
}
