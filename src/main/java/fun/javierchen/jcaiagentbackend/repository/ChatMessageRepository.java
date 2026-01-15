package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepository {

    private static final String TABLE_NAME = "chat_message";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setChatId(rs.getString("chat_id"));
        message.setTenantId(rs.getLong("tenant_id"));
        message.setUserId(rs.getLong("user_id"));
        message.setRole(rs.getString("role"));
        message.setClientMessageId(rs.getString("client_message_id"));
        message.setContent(rs.getString("content"));
        Object metadataValue = rs.getObject("metadata");
        message.setMetadata(metadataValue == null ? null : metadataValue.toString());
        message.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return message;
    };

    public Long insert(ChatMessage message) {
        String sql = """
                INSERT INTO %s (chat_id, tenant_id, user_id, role, client_message_id, content, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.formatted(TABLE_NAME);
        return jdbcTemplate.queryForObject(sql, Long.class,
                message.getChatId(),
                message.getTenantId(),
                message.getUserId(),
                message.getRole(),
                message.getClientMessageId(),
                message.getContent(),
                message.getMetadata(),
                Timestamp.valueOf(message.getCreatedAt()));
    }

    public boolean existsByClientMessageId(String chatId, Long userId, String role, String clientMessageId) {
        String sql = """
                SELECT 1 FROM %s
                WHERE chat_id = ? AND user_id = ? AND role = ? AND client_message_id = ?
                LIMIT 1
                """.formatted(TABLE_NAME);
        List<Integer> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1),
                chatId, userId, role, clientMessageId);
        return !results.isEmpty();
    }

    public ChatMessage findLatestByChatIdAndRole(String chatId, Long userId, String role) {
        String sql = """
                SELECT * FROM %s
                WHERE chat_id = ? AND user_id = ? AND role = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(TABLE_NAME);
        List<ChatMessage> results = jdbcTemplate.query(sql, rowMapper, chatId, userId, role);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<ChatMessage> listByChatIdForUser(String chatId, Long tenantId, Long userId,
                                                 Long beforeId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME)
                .append(" WHERE chat_id = ? AND tenant_id = ? AND user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(chatId);
        params.add(tenantId);
        params.add(userId);
        if (beforeId != null && beforeId > 0) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public List<ChatMessage> listByChatIdForAdmin(String chatId, Long beforeId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME)
                .append(" WHERE chat_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(chatId);
        if (beforeId != null && beforeId > 0) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }
}
