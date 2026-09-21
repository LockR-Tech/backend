package com.huynqb.laundrylocker.assistant.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/// Đoạn đã nhúng + tìm kiếm vector (pgvector, khoảng cách cosine `<=>`).
@Repository
public class ChunkRepository {

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /// Thay toàn bộ đoạn của tài liệu (đánh chỉ mục lại cũng đi đường này).
    public void replace(long documentId, List<TextChunker.Chunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings differ in size");
        }
        jdbc.update("DELETE FROM kb_chunks WHERE document_id = ?", documentId);
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.Chunk chunk = chunks.get(i);
            String heading = chunk.heading() == null || chunk.heading().length() <= 500
                    ? chunk.heading()
                    : chunk.heading().substring(0, 500);
            rows.add(new Object[] {documentId, i, heading, chunk.content(), vectorLiteral(embeddings.get(i))});
        }
        jdbc.batchUpdate(
                "INSERT INTO kb_chunks (document_id, ordinal, heading, content, embedding) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS vector))",
                rows);
    }

    /// Top `limit` đoạn gần câu hỏi nhất trong các tài liệu READY mà người hỏi được đọc.
    /// `readerRoles` null ⇒ không lọc (ADMIN).
    public List<RetrievedChunk> search(float[] query, List<String> readerRoles, int limit) {
        String vector = vectorLiteral(query);
        List<Object> args = new ArrayList<>();
        args.add(vector);
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.document_id, d.title, c.ordinal, c.heading, c.content, "
                        + "1 - (c.embedding <=> CAST(? AS vector)) AS score "
                        + "FROM kb_chunks c JOIN kb_documents d ON d.id = c.document_id "
                        + "WHERE d.status = 'READY'");
        if (readerRoles != null) {
            sql.append(" AND d.allowed_roles && CAST(? AS text[])");
            args.add(RoleSet.toArrayLiteral(readerRoles));
        }
        sql.append(" ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?");
        args.add(vector);
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new RetrievedChunk(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("title"),
                rs.getInt("ordinal"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getDouble("score")), args.toArray());
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder out = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            float value = vector[i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
            out.append(value);
        }
        return out.append(']').toString();
    }
}
