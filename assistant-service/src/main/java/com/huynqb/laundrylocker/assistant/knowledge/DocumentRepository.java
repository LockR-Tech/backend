package com.huynqb.laundrylocker.assistant.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {

    private static final String COLUMNS = "id, title, file_name, mime_type, status, allowed_roles, size_bytes, "
            + "chunk_count, error, created_by, created_at, updated_at, indexed_at";

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String title, String fileName, String mimeType, List<String> allowedRoles,
                       String checksum, byte[] content, Long createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO kb_documents (title, file_name, mime_type, allowed_roles, checksum, content, size_bytes, "
                        + "created_by) VALUES (?, ?, ?, CAST(? AS text[]), ?, ?, ?, ?) RETURNING id",
                Long.class,
                title, fileName, mimeType, RoleSet.toArrayLiteral(allowedRoles), checksum, content,
                (long) content.length, createdBy);
    }

    public boolean existsByChecksum(String checksum) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM kb_documents WHERE checksum = ?)", Boolean.class, checksum);
        return Boolean.TRUE.equals(exists);
    }

    public List<KnowledgeDocument> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM kb_documents ORDER BY id DESC", ROW_MAPPER);
    }

    public Optional<KnowledgeDocument> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM kb_documents WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Optional<KnowledgeDocument.Content> findContent(long id) {
        return jdbc.query(
                "SELECT id, title, file_name, mime_type, content FROM kb_documents WHERE id = ?",
                (rs, i) -> new KnowledgeDocument.Content(
                        rs.getLong("id"), rs.getString("title"), rs.getString("file_name"),
                        rs.getString("mime_type"), rs.getBytes("content")),
                id).stream().findFirst();
    }

    /// Nhận một tài liệu PENDING để đánh chỉ mục; SKIP LOCKED ⇒ nhiều instance không nhận trùng.
    public Optional<Long> claimNextPending() {
        return jdbc.queryForList(
                "UPDATE kb_documents SET status = 'INDEXING', error = NULL, updated_at = now() "
                        + "WHERE id = (SELECT id FROM kb_documents WHERE status = 'PENDING' ORDER BY id "
                        + "LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id",
                Long.class).stream().findFirst();
    }

    /// Instance chết giữa chừng ⇒ tài liệu kẹt INDEXING; lúc khởi động đưa về hàng đợi.
    public int requeueInterrupted() {
        return jdbc.update("UPDATE kb_documents SET status = 'PENDING', updated_at = now() WHERE status = 'INDEXING'");
    }

    public void markReady(long id, int chunkCount) {
        jdbc.update(
                "UPDATE kb_documents SET status = 'READY', chunk_count = ?, error = NULL, indexed_at = now(), "
                        + "updated_at = now() WHERE id = ?",
                chunkCount, id);
    }

    public void markFailed(long id, String error) {
        String message = error == null ? "Lỗi không xác định" : error;
        jdbc.update(
                "UPDATE kb_documents SET status = 'FAILED', error = ?, updated_at = now() WHERE id = ?",
                message.length() > 1000 ? message.substring(0, 1000) : message, id);
    }

    public boolean requeue(long id) {
        return jdbc.update(
                "UPDATE kb_documents SET status = 'PENDING', error = NULL, updated_at = now() WHERE id = ?", id) > 0;
    }

    public boolean updateRoles(long id, List<String> allowedRoles) {
        return jdbc.update(
                "UPDATE kb_documents SET allowed_roles = CAST(? AS text[]), updated_at = now() WHERE id = ?",
                RoleSet.toArrayLiteral(allowedRoles), id) > 0;
    }

    public boolean updateTitle(long id, String title) {
        return jdbc.update("UPDATE kb_documents SET title = ?, updated_at = now() WHERE id = ?", title, id) > 0;
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM kb_documents WHERE id = ?", id) > 0;
    }

    private static final RowMapper<KnowledgeDocument> ROW_MAPPER = (rs, rowNum) -> new KnowledgeDocument(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("file_name"),
            rs.getString("mime_type"),
            rs.getString("status"),
            roles(rs.getArray("allowed_roles")),
            rs.getLong("size_bytes"),
            rs.getInt("chunk_count"),
            rs.getString("error"),
            (Long) rs.getObject("created_by", Long.class),
            time(rs, "created_at"),
            time(rs, "updated_at"),
            time(rs, "indexed_at"));

    private static List<String> roles(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
