package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepository {

    private static final String TABLE_NAME = "chat_session";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatSession> rowMapper = (rs, rowNum) -> {
        ChatSession session = new ChatSession();
        session.setChatId(rs.getString("chat_id"));
        session.setTenantId(rs.getLong("tenant_id"));
        session.setUserId(rs.getLong("user_id"));
        session.setAppCode(rs.getString("app_code"));
        session.setTitle(rs.getString("title"));
        session.setLastMessageAt(rs.getTimestamp("last_message_at").toLocalDateTime());
        session.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        session.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        session.setIsDeleted(rs.getInt("is_deleted"));
        return session;
    };

    public void insert(ChatSession session) {
        String sql = """
                INSERT INTO %s (chat_id, tenant_id, user_id, app_code, title, last_message_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE_NAME);
        jdbcTemplate.update(sql,
                session.getChatId(),
                session.getTenantId(),
                session.getUserId(),
                session.getAppCode(),
                session.getTitle(),
                Timestamp.valueOf(session.getLastMessageAt()),
                Timestamp.valueOf(session.getCreatedAt()),
                Timestamp.valueOf(session.getUpdatedAt()),
                session.getIsDeleted());
    }

    public Optional<ChatSession> findActiveByChatId(String chatId) {
        String sql = "SELECT * FROM %s WHERE chat_id = ? AND is_deleted = 0".formatted(TABLE_NAME);
        List<ChatSession> results = jdbcTemplate.query(sql, rowMapper, chatId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<ChatSession> findActiveByChatIdAndUser(String chatId, Long tenantId, Long userId) {
        String sql = """
                SELECT * FROM %s
                WHERE chat_id = ? AND tenant_id = ? AND user_id = ? AND is_deleted = 0
                """.formatted(TABLE_NAME);
        List<ChatSession> results = jdbcTemplate.query(sql, rowMapper, chatId, tenantId, userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ChatSession> listByUser(Long tenantId, Long userId, String appCode,
                                        LocalDateTime beforeLastMessageAt, String beforeChatId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME)
                .append(" WHERE tenant_id = ? AND user_id = ? AND app_code = ? AND is_deleted = 0");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(userId);
        params.add(appCode);
        appendCursor(sql, params, beforeLastMessageAt, beforeChatId);
        sql.append(" ORDER BY last_message_at DESC, chat_id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public List<ChatSession> listByAdmin(Long tenantId, Long userId, String appCode,
                                         LocalDateTime beforeLastMessageAt, String beforeChatId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME)
                .append(" WHERE app_code = ? AND is_deleted = 0");
        List<Object> params = new ArrayList<>();
        params.add(appCode);
        if (tenantId != null) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        appendCursor(sql, params, beforeLastMessageAt, beforeChatId);
        sql.append(" ORDER BY last_message_at DESC, chat_id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public void updateLastMessageAt(String chatId, LocalDateTime lastMessageAt) {
        String sql = "UPDATE %s SET last_message_at = ?, updated_at = ? WHERE chat_id = ? AND is_deleted = 0"
                .formatted(TABLE_NAME);
        Timestamp ts = Timestamp.valueOf(lastMessageAt);
        jdbcTemplate.update(sql, ts, ts, chatId);
    }

    private void appendCursor(StringBuilder sql, List<Object> params,
                              LocalDateTime beforeLastMessageAt, String beforeChatId) {
        if (beforeLastMessageAt == null) {
            return;
        }
        if (beforeChatId != null && !beforeChatId.isBlank()) {
            sql.append(" AND (last_message_at < ? OR (last_message_at = ? AND chat_id < ?))");
            Timestamp ts = Timestamp.valueOf(beforeLastMessageAt);
            params.add(ts);
            params.add(ts);
            params.add(beforeChatId);
        } else {
            sql.append(" AND last_message_at < ?");
            params.add(Timestamp.valueOf(beforeLastMessageAt));
        }
    }
}
