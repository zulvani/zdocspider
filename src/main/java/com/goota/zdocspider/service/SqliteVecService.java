package com.goota.zdocspider.service;

import com.goota.zdocspider.model.dto.VectorSearchResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SqliteVecService {

    private final DataSource dataSource;
    private final boolean enabled;
    private final String configuredPath;
    private final int dimension;

    private boolean vecAvailable = false;
    private String resolvedExtensionPath;

    public SqliteVecService(
            DataSource dataSource,
            @Value("${sqlite.vec.enabled:true}") boolean enabled,
            @Value("${sqlite.vec.path:extensions/sqlite-vec/vec0.dylib}") String configuredPath,
            @Value("${embedding.dimension:384}") int dimension) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.configuredPath = configuredPath;
        this.dimension = dimension;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("sqlite-vec is disabled via configuration (sqlite.vec.enabled=false).");
            return;
        }

        resolvedExtensionPath = resolveExtensionPath(configuredPath);
        if (resolvedExtensionPath == null) {
            log.warn("sqlite-vec extension binary not found at configured path: '{}'.", configuredPath);
            log.warn("Vector storage will be skipped until sqlite-vec binary is placed or 'sqlite.vec.path' is configured in application.properties.");
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            ensureExtensionLoaded(conn);

            String version = getVecVersion(conn);
            log.info("sqlite-vec extension loaded successfully! Version: {}, binary: {}", version, resolvedExtensionPath);

            createVirtualTable(conn);
            vecAvailable = true;
            log.info("Initialized virtual table 'doc_vectors' with dimension {}.", dimension);
        } catch (Exception e) {
            log.error("Failed to initialize sqlite-vec extension from '{}': {}.", resolvedExtensionPath, e.getMessage(), e);
            log.error("Instructions: Ensure sqlite-vec binary matches your operating system/architecture and sqlite-jdbc allows loading extensions ('enable_load_extension=true').");
            vecAvailable = false;
        }
    }

    public boolean isVectorAvailable() {
        return vecAvailable;
    }

    public void saveDocVectors(String docId, float[] fileNameVector, float[] contentVector) {
        if (!vecAvailable) {
            log.debug("sqlite-vec not available, skipping vector save for docId: {}", docId);
            return;
        }

        if (docId == null || docId.trim().isEmpty()) {
            log.warn("Cannot save vectors with empty docId");
            return;
        }

        String fileNameVectorJson = formatVectorToJson(fileNameVector, dimension);
        String contentVectorJson = formatVectorToJson(contentVector, dimension);

        Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try {
            ensureExtensionLoaded(conn);

            // Delete existing vector row for this docId (idempotent for re-scans)
            try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM doc_vectors WHERE doc_id = ?")) {
                deleteStmt.setString(1, docId);
                deleteStmt.executeUpdate();
            }

            // Insert new vector row using vec_f32()
            String insertSql = "INSERT INTO doc_vectors(doc_id, file_name_embedding, content_embedding) VALUES (?, vec_f32(?), vec_f32(?))";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, docId);
                insertStmt.setString(2, fileNameVectorJson);
                insertStmt.setString(3, contentVectorJson);
                insertStmt.executeUpdate();
            }

            log.debug("Successfully saved vectors for docId: {}", docId);
        } catch (SQLException e) {
            log.error("Failed to save vectors for docId {}: {}", docId, e.getMessage(), e);
            throw new RuntimeException("Failed to save doc vectors in sqlite-vec: " + e.getMessage(), e);
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public void clearAll() {
        if (!vecAvailable) return;
        Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try (Statement stmt = conn.createStatement()) {
            ensureExtensionLoaded(conn);
            stmt.execute("DELETE FROM doc_vectors");
        } catch (SQLException e) {
            log.error("Failed to clear doc_vectors: {}", e.getMessage(), e);
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public List<VectorSearchResult> searchByContentVector(float[] queryVector, int limit) {
        return searchByVectorColumn("content_embedding", queryVector, limit);
    }

    public List<VectorSearchResult> searchByFileNameVector(float[] queryVector, int limit) {
        return searchByVectorColumn("file_name_embedding", queryVector, limit);
    }

    private List<VectorSearchResult> searchByVectorColumn(String columnName, float[] queryVector, int limit) {
        List<VectorSearchResult> results = new ArrayList<>();
        if (!vecAvailable) {
            log.warn("sqlite-vec is not available for search");
            return results;
        }

        int k = limit > 0 ? limit : 10;
        String vectorJson = formatVectorToJson(queryVector, dimension);
        String querySql = "SELECT doc_id, distance FROM doc_vectors WHERE " + columnName + " MATCH vec_f32(?) AND k = ? ORDER BY distance";

        Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try {
            ensureExtensionLoaded(conn);

            try (PreparedStatement stmt = conn.prepareStatement(querySql)) {
                stmt.setString(1, vectorJson);
                stmt.setInt(2, k);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(VectorSearchResult.builder()
                                .docId(rs.getString("doc_id"))
                                .distance(rs.getDouble("distance"))
                                .build());
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed executing vector search on column {}: {}", columnName, e.getMessage(), e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, dataSource);
        }

        return results;
    }

    private void createVirtualTable(Connection conn) throws SQLException {
        // If doc_vectors exists with a different vector dimension, recreate it
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='doc_vectors'")) {
            if (rs.next()) {
                String existingSql = rs.getString(1);
                String expectedCol = "float[" + dimension + "]";
                if (existingSql != null && !existingSql.contains(expectedCol)) {
                    log.warn("Existing doc_vectors table has different dimension schema. Dropping and recreating with dimension {}...", dimension);
                    checkStmt.execute("DROP TABLE doc_vectors");
                }
            }
        } catch (Exception e) {
            log.debug("Error checking existing doc_vectors schema: {}", e.getMessage());
        }

        String ddl = String.format(
                "CREATE VIRTUAL TABLE IF NOT EXISTS doc_vectors USING vec0(" +
                        "doc_id text partition key," +
                        "file_name_embedding float[%d]," +
                        "content_embedding float[%d]" +
                        ")",
                dimension, dimension
        );
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    private void ensureExtensionLoaded(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT vec_version()");
            return; // Already loaded on this connection
        } catch (SQLException ignored) {
            // Not loaded yet on this connection, proceed to load
        }

        try (Statement stmt = conn.createStatement()) {
            // SQLite load_extension accepts absolute path or relative path
            String escapedPath = resolvedExtensionPath.replace("'", "''");
            stmt.execute("SELECT load_extension('" + escapedPath + "')");
        }
    }

    private String getVecVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT vec_version()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return "unknown";
    }

    private String resolveExtensionPath(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }

        Path directPath = Paths.get(configured);
        if (Files.exists(directPath)) {
            return directPath.toAbsolutePath().toString();
        }

        // Try relative to user working directory
        Path localPath = Paths.get(System.getProperty("user.dir"), configured);
        if (Files.exists(localPath)) {
            return localPath.toAbsolutePath().toString();
        }

        // Try OS-specific fallbacks under extensions/sqlite-vec
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> candidates = new ArrayList<>();
        if (os.contains("mac")) {
            candidates.add("extensions/sqlite-vec/vec0.dylib");
            candidates.add("extensions/sqlite-vec/vec0");
        } else if (os.contains("linux")) {
            candidates.add("extensions/sqlite-vec/vec0.so");
            candidates.add("extensions/sqlite-vec/vec0-linux-x86_64.so");
            candidates.add("extensions/sqlite-vec/vec0");
        } else if (os.contains("win")) {
            candidates.add("extensions/sqlite-vec/vec0.dll");
        }

        for (String candidate : candidates) {
            Path p = Paths.get(candidate);
            if (Files.exists(p)) {
                return p.toAbsolutePath().toString();
            }
            Path pLocal = Paths.get(System.getProperty("user.dir"), candidate);
            if (Files.exists(pLocal)) {
                return pLocal.toAbsolutePath().toString();
            }
        }

        return null;
    }

    private String formatVectorToJson(float[] vector, int expectedDim) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int len = vector != null ? vector.length : 0;
        for (int i = 0; i < expectedDim; i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (i < len) {
                sb.append(vector[i]);
            } else {
                sb.append("0.0");
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
