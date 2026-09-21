package com.huynqb.laundrylocker.assistant.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    public record ConversationRow(
            long id, long userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt, int messageCount) {
    }

    public record MessageRow(
            long id, long conversationId, String role, String content, String citationsJson, boolean refused,
            Double topScore, String model, LocalDateTime createdAt) {
    }

    private static final String CONVERSATION_COLUMNS = "c.id, c.user_id, c.title, c.created_at, c.updated_at, "
            + "(SELECT count(*) FROM assistant_messages m WHERE m.conversation_id = c.id) AS message_count";

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long userId, String title) {
        return jdbc.queryForObject(
                "INSERT INTO assistant_conversations (user_id, title) VALUES (?, ?) RETURNING id",
                Long.class, userId, title);
    }

    public Optional<ConversationRow> find(long id) {
        return jdbc.query(
                "SELECT " + CONVERSATION_COLUMNS + " FROM assistant_conversations c WHERE c.id = ?",
                CONVERSATION_MAPPER, id).stream().findFirst();
    }

    public List<ConversationRow> listByUser(long userId, int limit) {
        return jdbc.query(
                "SELECT " + CONVERSATION_COLUMNS + " FROM assistant_conversations c WHERE c.user_id = ? "
                        + "ORDER BY c.updated_at DESC, c.id DESC LIMIT ?",
                CONVERSATION_MAPPER, userId, limit);
    }

    public List<ConversationRow> listRecent(Long userId, int limit, int offset) {
        if (userId != null) {
            return jdbc.query(
                    "SELECT " + CONVERSATION_COLUMNS + " FROM assistant_conversations c WHERE c.user_id = ? "
                            + "ORDER BY c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?",
                    CONVERSATION_MAPPER, userId, limit, offset);
        }
        return jdbc.query(
                "SELECT " + CONVERSATION_COLUMNS + " FROM assistant_conversations c "
                        + "ORDER BY c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?",
                CONVERSATION_MAPPER, limit, offset);
    }

    public boolean delete(long id, long userId) {
        return jdbc.update("DELETE FROM assistant_conversations WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    public void touch(long id) {
        jdbc.update("UPDATE assistant_conversations SET updated_at = now() WHERE id = ?", id);
    }

    public long addMessage(long conversationId, String role, String content, String citationsJson, boolean refused,
                           Double topScore, String model, Long inputTokens, Long outputTokens) {
        return jdbc.queryForObject(
                "INSERT INTO assistant_messages (conversation_id, role, content, citations, refused, top_score, model, "
                        + "input_tokens, output_tokens) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                conversationId, role, content, citationsJson, refused, topScore, model,
                inputTokens == null ? null : inputTokens.intValue(),
                outputTokens == null ? null : outputTokens.intValue());
    }

    public List<MessageRow> messages(long conversationId) {
        return jdbc.query(
                "SELECT * FROM assistant_messages WHERE conversation_id = ? ORDER BY id",
                MESSAGE_MAPPER, conversationId);
    }

    /// `limit` tin nhắn cuối, theo thứ tự thời gian.
    public List<MessageRow> lastMessages(long conversationId, int limit) {
        return jdbc.query(
                "SELECT * FROM (SELECT * FROM assistant_messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?) t "
                        + "ORDER BY id",
                MESSAGE_MAPPER, conversationId, limit);
    }

    public long countQuestionsSince(long userId, LocalDateTime since) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM assistant_messages m JOIN assistant_conversations c ON c.id = m.conversation_id "
                        + "WHERE c.user_id = ? AND m.role = 'USER' AND m.created_at >= ?",
                Long.class, userId, Timestamp.valueOf(since));
        return count == null ? 0 : count;
    }

    private static final RowMapper<ConversationRow> CONVERSATION_MAPPER = (rs, i) -> new ConversationRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            time(rs, "created_at"),
            time(rs, "updated_at"),
            rs.getInt("message_count"));

    private static final RowMapper<MessageRow> MESSAGE_MAPPER = (rs, i) -> new MessageRow(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getString("citations"),
            rs.getBoolean("refused"),
            rs.getObject("top_score", Double.class),
            rs.getString("model"),
            time(rs, "created_at"));

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
