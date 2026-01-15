package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.rag.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.rag.model.enums.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 学习助手文档数据访问层
 * 对应表 study_friend_document
 *
 * @author JavierChen
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class StudyFriendDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String TABLE_NAME = "study_friend_document";

    /**
     * 行映射器
     */
    private final RowMapper<StudyFriendDocument> rowMapper = (rs, rowNum) -> {
        StudyFriendDocument doc = new StudyFriendDocument();
        doc.setId(rs.getLong("id"));
        doc.setTenantId(rs.getLong("tenant_id"));
        doc.setOwnerUserId(rs.getLong("owner_user_id"));
        doc.setFileName(rs.getString("file_name"));
        doc.setFilePath(rs.getString("file_path"));
        doc.setFileType(rs.getString("file_type"));
        doc.setStatus(DocumentStatus.fromCode(rs.getString("status")));
        doc.setErrorMessage(rs.getString("error_message"));
        doc.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        doc.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return doc;
    };

    /**
     * 保存文档
     */
    public StudyFriendDocument save(StudyFriendDocument document) {
        if (document.getId() == null) {
            return insert(document);
        } else {
            return update(document);
        }
    }

    /**
     * 插入新文档
     * 使用 PostgreSQL 的 RETURNING id 语法获取生成的主键
     */
    private StudyFriendDocument insert(StudyFriendDocument document) {
        String sql = """
                    INSERT INTO %s (tenant_id, owner_user_id, file_name, file_path, file_type, status, error_message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                """.formatted(TABLE_NAME);

        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        Long generatedId = jdbcTemplate.queryForObject(sql, Long.class,
                document.getTenantId(),
                document.getOwnerUserId(),
                document.getFileName(),
                document.getFilePath(),
                document.getFileType(),
                document.getStatus().getCode(),
                document.getErrorMessage(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now));

        document.setId(generatedId);
        return document;
    }

    /**
     * 更新文档
     */
    private StudyFriendDocument update(StudyFriendDocument document) {
        String sql = """
                    UPDATE %s SET file_name = ?, file_path = ?, file_type = ?,
                    status = ?, error_message = ?, updated_at = ?
                    WHERE id = ?
                """.formatted(TABLE_NAME);

        LocalDateTime now = LocalDateTime.now();
        document.setUpdatedAt(now);

        jdbcTemplate.update(sql,
                document.getFileName(),
                document.getFilePath(),
                document.getFileType(),
                document.getStatus().getCode(),
                document.getErrorMessage(),
                Timestamp.valueOf(now),
                document.getId());

        return document;
    }

    /**
     * 根据ID查询
     */
    public Optional<StudyFriendDocument> findById(Long id) {
        String sql = "SELECT * FROM %s WHERE id = ?".formatted(TABLE_NAME);
        List<StudyFriendDocument> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据ID和租户查询
     */
    public Optional<StudyFriendDocument> findByIdAndTenantId(Long id, Long tenantId) {
        String sql = "SELECT * FROM %s WHERE id = ? AND tenant_id = ?".formatted(TABLE_NAME);
        List<StudyFriendDocument> results = jdbcTemplate.query(sql, rowMapper, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据文件名和文件类型查询（用于去重）
     */
    public StudyFriendDocument findByFileNameAndType(String fileName, String fileType, Long tenantId) {
        String sql = "SELECT * FROM %s WHERE file_name = ? AND file_type = ? AND tenant_id = ? LIMIT 1".formatted(TABLE_NAME);
        List<StudyFriendDocument> results = jdbcTemplate.query(sql, rowMapper, fileName, fileType, tenantId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 更新文档状态
     */
    public void updateStatus(Long id, DocumentStatus status, String errorMessage) {
        String sql = """
                    UPDATE %s SET status = ?, error_message = ?, updated_at = ? WHERE id = ?
                """.formatted(TABLE_NAME);
        jdbcTemplate.update(sql, status.getCode(), errorMessage, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    /**
     * 查询需要重试的文档（状态为 FAILED）
     */
    public List<StudyFriendDocument> findByStatus(DocumentStatus status) {
        String sql = "SELECT * FROM %s WHERE status = ? ORDER BY updated_at ASC".formatted(TABLE_NAME);
        return jdbcTemplate.query(sql, rowMapper, status.getCode());
    }

    /**
     * 查询需要重试的文档（状态为 FAILED，限制数量）
     */
    public List<StudyFriendDocument> findFailedDocuments(int limit) {
        String sql = "SELECT * FROM %s WHERE status = ? ORDER BY updated_at ASC LIMIT ?".formatted(TABLE_NAME);
        return jdbcTemplate.query(sql, rowMapper, DocumentStatus.FAILED.getCode(), limit);
    }

    /**
     * 查询处理中超时的文档（超过指定分钟数仍为 INDEXING 状态）
     */
    public List<StudyFriendDocument> findTimeoutIndexingDocuments(int timeoutMinutes) {
        String sql = """
                    SELECT * FROM %s WHERE status = ? AND updated_at < ? ORDER BY updated_at ASC LIMIT 10
                """.formatted(TABLE_NAME);
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return jdbcTemplate.query(sql, rowMapper, DocumentStatus.INDEXING.getCode(), Timestamp.valueOf(timeout));
    }

    /**
     * 根据ID删除
     */
    public void deleteById(Long id) {
        String sql = "DELETE FROM %s WHERE id = ?".formatted(TABLE_NAME);
        jdbcTemplate.update(sql, id);
    }

    /**
     * 根据ID和租户删除
     */
    public void deleteByIdAndTenantId(Long id, Long tenantId) {
        String sql = "DELETE FROM %s WHERE id = ? AND tenant_id = ?".formatted(TABLE_NAME);
        jdbcTemplate.update(sql, id, tenantId);
    }

    /**
     * 查询所有文档
     */
    public List<StudyFriendDocument> findAll() {
        String sql = "SELECT * FROM %s ORDER BY created_at DESC".formatted(TABLE_NAME);
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 按租户查询所有文档
     */
    public List<StudyFriendDocument> findAllByTenantId(Long tenantId) {
        String sql = "SELECT * FROM %s WHERE tenant_id = ? ORDER BY created_at DESC".formatted(TABLE_NAME);
        return jdbcTemplate.query(sql, rowMapper, tenantId);
    }
}
