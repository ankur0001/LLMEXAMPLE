package com.projectmind.adapter.memory;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Global SQLite store for cross-project metadata and queryable graph edges.
 */
final class SqliteMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteMetadataStore.class);

    private final String jdbcUrl;

    SqliteMetadataStore(String databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + resolveDatabasePath(databasePath);
        initializeSchema();
    }

    void upsertProject(String repositoryPath, ProjectMetadata metadata) {
        execute(connection -> {
            long projectId = ensureProjectId(connection, repositoryPath, metadata.name());
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE projects
                    SET name = ?, last_scanned_at = ?, total_files = ?, status = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, metadata.name());
                statement.setString(2, metadata.lastScannedAt() != null ? metadata.lastScannedAt().toString() : null);
                statement.setInt(3, metadata.totalFiles());
                statement.setString(4, metadata.status().name());
                statement.setLong(5, projectId);
                statement.executeUpdate();
            }
        });
    }

    void syncGraph(String repositoryPath, KnowledgeGraph graph) {
        execute(connection -> {
            long projectId = ensureProjectId(connection, repositoryPath, Path.of(repositoryPath).getFileName().toString());
            try (PreparedStatement deleteEdges = connection.prepareStatement("DELETE FROM graph_edges WHERE project_id = ?");
                 PreparedStatement deleteNodes = connection.prepareStatement("DELETE FROM graph_nodes WHERE project_id = ?")) {
                deleteEdges.setLong(1, projectId);
                deleteEdges.executeUpdate();
                deleteNodes.setLong(1, projectId);
                deleteNodes.executeUpdate();
            }

            try (PreparedStatement insertNode = connection.prepareStatement("""
                    INSERT INTO graph_nodes (project_id, node_id, name, node_type, source_file, package_name)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                for (GraphNode node : graph.nodes()) {
                    insertNode.setLong(1, projectId);
                    insertNode.setString(2, node.id());
                    insertNode.setString(3, node.name());
                    insertNode.setString(4, node.type().name());
                    insertNode.setString(5, node.sourceFile());
                    insertNode.setString(6, node.packageName());
                    insertNode.addBatch();
                }
                insertNode.executeBatch();
            }

            try (PreparedStatement insertEdge = connection.prepareStatement("""
                    INSERT INTO graph_edges (project_id, source_id, target_id, edge_type, label)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (GraphEdge edge : graph.edges()) {
                    insertEdge.setLong(1, projectId);
                    insertEdge.setString(2, edge.sourceId());
                    insertEdge.setString(3, edge.targetId());
                    insertEdge.setString(4, edge.type().name());
                    insertEdge.setString(5, edge.label());
                    insertEdge.addBatch();
                }
                insertEdge.executeBatch();
            }
        });
    }

    void upsertSummary(String repositoryPath, String summaryType, String entityKey, String summaryJson, Instant updatedAt) {
        execute(connection -> {
            long projectId = ensureProjectId(connection, repositoryPath, Path.of(repositoryPath).getFileName().toString());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO summaries (project_id, summary_type, entity_key, summary_json, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(project_id, summary_type, entity_key)
                    DO UPDATE SET summary_json = excluded.summary_json, updated_at = excluded.updated_at
                    """)) {
                statement.setLong(1, projectId);
                statement.setString(2, summaryType);
                statement.setString(3, entityKey);
                statement.setString(4, summaryJson);
                statement.setString(5, updatedAt.toString());
                statement.executeUpdate();
            }
        });
    }

    void deleteSummary(String repositoryPath, String summaryType, String entityKey) {
        execute(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM summaries
                    WHERE project_id = ? AND summary_type = ? AND entity_key = ?
                    """)) {
                statement.setLong(1, projectId.get());
                statement.setString(2, summaryType);
                statement.setString(3, entityKey);
                statement.executeUpdate();
            }
        });
    }

    void addHistoryEntry(String repositoryPath, HistorySnapshot snapshot) {
        execute(connection -> {
            long projectId = ensureProjectId(connection, repositoryPath, Path.of(repositoryPath).getFileName().toString());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO history (project_id, timestamp, operation, files_changed, description)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, projectId);
                statement.setString(2, snapshot.timestamp().toString());
                statement.setString(3, snapshot.operation());
                statement.setInt(4, snapshot.filesChanged());
                statement.setString(5, snapshot.description());
                statement.executeUpdate();
            }
        });
    }

    List<HistorySnapshot> listHistory(String repositoryPath, int limit) {
        return query(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return List.of();
            }

            List<HistorySnapshot> snapshots = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT timestamp, operation, files_changed, description
                    FROM history
                    WHERE project_id = ?
                    ORDER BY timestamp DESC
                    LIMIT ?
                    """)) {
                statement.setLong(1, projectId.get());
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        snapshots.add(new HistorySnapshot(
                                Instant.parse(rs.getString("timestamp")),
                                rs.getString("operation"),
                                rs.getInt("files_changed"),
                                rs.getString("description")));
                    }
                }
            }
            return snapshots;
        });
    }

    int countSummaries(String repositoryPath, String summaryType) {
        return query(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return 0;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*) AS total
                    FROM summaries
                    WHERE project_id = ? AND summary_type = ?
                    """)) {
                statement.setLong(1, projectId.get());
                statement.setString(2, summaryType);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt("total") : 0;
                }
            }
        });
    }

    GraphCounts countGraph(String repositoryPath) {
        return query(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return new GraphCounts(0, 0);
            }
            int nodes = countForProject(connection, "graph_nodes", projectId.get());
            int edges = countForProject(connection, "graph_edges", projectId.get());
            return new GraphCounts(nodes, edges);
        });
    }

    int countHistory(String repositoryPath) {
        return query(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return 0;
            }
            return countForProject(connection, "history", projectId.get());
        });
    }

    void deleteProject(String repositoryPath) {
        execute(connection -> {
            Optional<Long> projectId = findProjectId(connection, repositoryPath);
            if (projectId.isEmpty()) {
                return;
            }
            deleteByProjectId(connection, "graph_edges", projectId.get());
            deleteByProjectId(connection, "graph_nodes", projectId.get());
            deleteByProjectId(connection, "summaries", projectId.get());
            deleteByProjectId(connection, "history", projectId.get());
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM projects WHERE id = ?")) {
                statement.setLong(1, projectId.get());
                statement.executeUpdate();
            }
        });
    }

    private void initializeSchema() {
        execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS projects (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            repository_path TEXT NOT NULL UNIQUE,
                            name TEXT,
                            last_scanned_at TEXT,
                            total_files INTEGER DEFAULT 0,
                            status TEXT
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS graph_nodes (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            project_id INTEGER NOT NULL,
                            node_id TEXT NOT NULL,
                            name TEXT,
                            node_type TEXT,
                            source_file TEXT,
                            package_name TEXT,
                            UNIQUE(project_id, node_id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS graph_edges (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            project_id INTEGER NOT NULL,
                            source_id TEXT NOT NULL,
                            target_id TEXT NOT NULL,
                            edge_type TEXT,
                            label TEXT
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS summaries (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            project_id INTEGER NOT NULL,
                            summary_type TEXT NOT NULL,
                            entity_key TEXT NOT NULL,
                            summary_json TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            UNIQUE(project_id, summary_type, entity_key)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            project_id INTEGER NOT NULL,
                            timestamp TEXT NOT NULL,
                            operation TEXT NOT NULL,
                            files_changed INTEGER DEFAULT 0,
                            description TEXT
                        )
                        """);
            }
        });
    }

    private long ensureProjectId(Connection connection, String repositoryPath, String name) throws SQLException {
        Optional<Long> existing = findProjectId(connection, repositoryPath);
        if (existing.isPresent()) {
            return existing.get();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO projects (repository_path, name, total_files, status)
                VALUES (?, ?, 0, 'UNKNOWN')
                """)) {
            statement.setString(1, repositoryPath);
            statement.setString(2, name);
            statement.executeUpdate();
        }
        return findProjectId(connection, repositoryPath).orElseThrow();
    }

    private Optional<Long> findProjectId(Connection connection, String repositoryPath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM projects WHERE repository_path = ?")) {
            statement.setString(1, repositoryPath);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("id"));
                }
            }
        }
        return Optional.empty();
    }

    private int countForProject(Connection connection, String table, long projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) AS total FROM " + table + " WHERE project_id = ?")) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    private void deleteByProjectId(Connection connection, String table, long projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE project_id = ?")) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }

    private static String resolveDatabasePath(String configuredPath) {
        String expanded = configuredPath.startsWith("~")
                ? System.getProperty("user.home") + configuredPath.substring(1)
                : configuredPath;
        Path path = Path.of(expanded);
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            log.warn("Could not create SQLite parent directory for {}: {}", path, e.getMessage());
        }
        return path.toString();
    }

    private void execute(SqlCallback callback) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(true);
            callback.run(connection);
        } catch (SQLException e) {
            throw new RuntimeException("SQLite operation failed", e);
        }
    }

    private <T> T query(SqlQuery<T> callback) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(true);
            return callback.run(connection);
        } catch (SQLException e) {
            throw new RuntimeException("SQLite query failed", e);
        }
    }

    @FunctionalInterface
    private interface SqlCallback {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlQuery<T> {
        T run(Connection connection) throws SQLException;
    }

    record GraphCounts(int nodeCount, int edgeCount) {
    }
}
