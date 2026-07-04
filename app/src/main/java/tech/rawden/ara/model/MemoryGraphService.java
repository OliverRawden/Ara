package tech.rawden.ara.model;

import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.AppLog;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Embedded SQLite store for entity/relation memory at {@link AraPaths#memoryGraphDb()}.
 * Queryable by agent tools and editable from Vex's Memory Graph view.
 */
public final class MemoryGraphService {

    private static final Logger LOG = AppLog.of("memory-graph");
    private static final MemoryGraphService INSTANCE = new MemoryGraphService();

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOG.warning("SQLite JDBC driver not found: " + e.getMessage());
        }
    }

    private MemoryGraphService() {
        initSchema();
    }

    public static MemoryGraphService get() {
        return INSTANCE;
    }

    public record Entity(String id, String kind, String label, String content, Instant updatedAt) {}

    public record Relation(long id, String fromId, String toId, String relationType, String note, Instant createdAt) {}

    public record QueryResult(List<Entity> entities, List<Relation> relations) {}

    private synchronized Connection connect() throws SQLException {
        try {
            Files.createDirectories(AraPaths.dataDir());
        } catch (Exception e) {
            throw new SQLException("Could not create data directory", e);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + AraPaths.memoryGraphDb());
    }

    private void initSchema() {
        try (var conn = connect(); var st = conn.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS entities (
                        id TEXT PRIMARY KEY,
                        kind TEXT NOT NULL DEFAULT 'note',
                        label TEXT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""");
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS relations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        from_id TEXT NOT NULL,
                        to_id TEXT NOT NULL,
                        relation_type TEXT NOT NULL DEFAULT 'related_to',
                        note TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (from_id) REFERENCES entities(id) ON DELETE CASCADE,
                        FOREIGN KEY (to_id) REFERENCES entities(id) ON DELETE CASCADE
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_entities_label ON entities(label)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_entities_kind ON entities(kind)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_relations_from ON relations(from_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_relations_to ON relations(to_id)");
        } catch (SQLException e) {
            LOG.warning("Memory graph schema init failed: " + e.getMessage());
        }
    }

    public Entity upsertEntity(String id, String kind, String label, String content) throws SQLException {
        var now = Instant.now().toString();
        var entityId = id != null && !id.isBlank() ? id.strip() : java.util.UUID.randomUUID().toString();
        var entityKind = kind != null && !kind.isBlank() ? kind.strip() : "note";
        var entityLabel = label != null ? label.strip() : "";
        var entityContent = content != null ? content : "";
        if (entityLabel.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }

        try (var conn = connect()) {
            boolean exists;
            try (var check = conn.prepareStatement("SELECT 1 FROM entities WHERE id = ?")) {
                check.setString(1, entityId);
                try (var rs = check.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                try (var ps = conn.prepareStatement(
                        "UPDATE entities SET kind = ?, label = ?, content = ?, updated_at = ? WHERE id = ?")) {
                    ps.setString(1, entityKind);
                    ps.setString(2, entityLabel);
                    ps.setString(3, entityContent);
                    ps.setString(4, now);
                    ps.setString(5, entityId);
                    ps.executeUpdate();
                }
            } else {
                try (var ps = conn.prepareStatement(
                        "INSERT INTO entities (id, kind, label, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, entityId);
                    ps.setString(2, entityKind);
                    ps.setString(3, entityLabel);
                    ps.setString(4, entityContent);
                    ps.setString(5, now);
                    ps.setString(6, now);
                    ps.executeUpdate();
                }
            }
        }
        return new Entity(entityId, entityKind, entityLabel, entityContent, Instant.parse(now));
    }

    public Relation linkEntities(String fromId, String toId, String relationType, String note) throws SQLException {
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("from_id and to_id are required");
        }
        var type = relationType != null && !relationType.isBlank() ? relationType.strip() : "related_to";
        var now = Instant.now().toString();

        try (var conn = connect()) {
            ensureEntityExists(conn, fromId.strip());
            ensureEntityExists(conn, toId.strip());
            long relationId;
            try (var ps = conn.prepareStatement(
                    "INSERT INTO relations (from_id, to_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, fromId.strip());
                ps.setString(2, toId.strip());
                ps.setString(3, type);
                ps.setString(4, note);
                ps.setString(5, now);
                ps.executeUpdate();
                try (var keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No relation id generated");
                    }
                    relationId = keys.getLong(1);
                }
            }
            return new Relation(relationId, fromId.strip(), toId.strip(), type, note, Instant.parse(now));
        }
    }

    public QueryResult query(String queryText, String kindFilter, int limit) throws SQLException {
        int max = limit > 0 ? Math.min(limit, 50) : 20;
        var entities = new ArrayList<Entity>();
        var relations = new ArrayList<Relation>();

        try (var conn = connect()) {
            if (queryText == null || queryText.isBlank()) {
                entities.addAll(listEntities(conn, kindFilter, max));
            } else {
                var pattern = "%" + queryText.strip().toLowerCase() + "%";
                var sql = new StringBuilder(
                        "SELECT id, kind, label, content, updated_at FROM entities WHERE (LOWER(label) LIKE ? OR LOWER(content) LIKE ? OR LOWER(id) LIKE ?)");
                if (kindFilter != null && !kindFilter.isBlank()) {
                    sql.append(" AND kind = ?");
                }
                sql.append(" ORDER BY updated_at DESC LIMIT ?");

                try (var ps = conn.prepareStatement(sql.toString())) {
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                    ps.setString(3, pattern);
                    int idx = 4;
                    if (kindFilter != null && !kindFilter.isBlank()) {
                        ps.setString(idx++, kindFilter.strip());
                    }
                    ps.setInt(idx, max);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            entities.add(readEntity(rs));
                        }
                    }
                }
            }

            if (!entities.isEmpty()) {
                var ids = entities.stream().map(Entity::id).toList();
                relations.addAll(relationsForEntities(conn, ids, max));
            }
        }
        return new QueryResult(entities, relations);
    }

    public List<Entity> listAllEntities() throws SQLException {
        try (var conn = connect()) {
            return listEntities(conn, null, 500);
        }
    }

    public List<Relation> listAllRelations() throws SQLException {
        try (var conn = connect()) {
            return listRelations(conn, 500);
        }
    }

    public void deleteEntity(String id) throws SQLException {
        try (var conn = connect();
                var ps = conn.prepareStatement("DELETE FROM entities WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteRelation(long id) throws SQLException {
        try (var conn = connect();
                var ps = conn.prepareStatement("DELETE FROM relations WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public String formatForAgent(QueryResult result) {
        if (result.entities().isEmpty() && result.relations().isEmpty()) {
            return "(no matching entities or relations)";
        }
        var sb = new StringBuilder();
        if (!result.entities().isEmpty()) {
            sb.append("Entities:\n");
            for (var e : result.entities()) {
                sb.append("- [")
                        .append(e.id())
                        .append("] ")
                        .append(e.kind())
                        .append(" · ")
                        .append(e.label());
                if (e.content() != null && !e.content().isBlank()) {
                    var snippet = e.content().length() > 240 ? e.content().substring(0, 237) + "…" : e.content();
                    sb.append(" — ").append(snippet.replace('\n', ' '));
                }
                sb.append('\n');
            }
        }
        if (!result.relations().isEmpty()) {
            sb.append("\nRelations:\n");
            for (var r : result.relations()) {
                sb.append("- ")
                        .append(r.fromId())
                        .append(" -[")
                        .append(r.relationType())
                        .append("]-> ")
                        .append(r.toId());
                if (r.note() != null && !r.note().isBlank()) {
                    sb.append(" (").append(r.note()).append(')');
                }
                sb.append('\n');
            }
        }
        return sb.toString().strip();
    }

    private static void ensureEntityExists(Connection conn, String id) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT 1 FROM entities WHERE id = ?")) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Unknown entity id: " + id);
                }
            }
        }
    }

    private static List<Entity> listEntities(Connection conn, String kindFilter, int limit) throws SQLException {
        var entities = new ArrayList<Entity>();
        var sql = "SELECT id, kind, label, content, updated_at FROM entities";
        if (kindFilter != null && !kindFilter.isBlank()) {
            sql += " WHERE kind = ?";
        }
        sql += " ORDER BY updated_at DESC LIMIT ?";
        try (var ps = conn.prepareStatement(sql)) {
            if (kindFilter != null && !kindFilter.isBlank()) {
                ps.setString(1, kindFilter.strip());
                ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
            }
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    entities.add(readEntity(rs));
                }
            }
        }
        return entities;
    }

    private static List<Relation> listRelations(Connection conn, int limit) throws SQLException {
        var relations = new ArrayList<Relation>();
        try (var ps = conn.prepareStatement(
                "SELECT id, from_id, to_id, relation_type, note, created_at FROM relations ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(readRelation(rs));
                }
            }
        }
        return relations;
    }

    private static List<Relation> relationsForEntities(Connection conn, List<String> ids, int limit) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var sql = "SELECT id, from_id, to_id, relation_type, note, created_at FROM relations WHERE from_id IN ("
                + placeholders
                + ") OR to_id IN ("
                + placeholders
                + ") ORDER BY id DESC LIMIT ?";
        var relations = new ArrayList<Relation>();
        try (var ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (var id : ids) {
                ps.setString(idx++, id);
            }
            for (var id : ids) {
                ps.setString(idx++, id);
            }
            ps.setInt(idx, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(readRelation(rs));
                }
            }
        }
        return relations;
    }

    private static Entity readEntity(ResultSet rs) throws SQLException {
        return new Entity(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("label"),
                rs.getString("content"),
                Instant.parse(rs.getString("updated_at")));
    }

    private static Relation readRelation(ResultSet rs) throws SQLException {
        return new Relation(
                rs.getLong("id"),
                rs.getString("from_id"),
                rs.getString("to_id"),
                rs.getString("relation_type"),
                rs.getString("note"),
                Instant.parse(rs.getString("created_at")));
    }
}