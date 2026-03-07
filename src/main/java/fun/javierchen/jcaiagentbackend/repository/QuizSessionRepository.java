package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 测验会话 Repository
 *
 * @author JavierChen
 */
public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID>,
        JpaSpecificationExecutor<QuizSession> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的会话
     */
    Optional<QuizSession> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的会话 (默认未删除)
     */
    default Optional<QuizSession> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询用户的所有会话 (按创建时间降序)
     */
    List<QuizSession> findByUserIdAndIsDeleteOrderByCreateTimeDesc(Long userId, Integer isDelete);

    /**
     * 查询用户的所有会话 (默认未删除)
     */
    default List<QuizSession> findActiveByUserId(Long userId) {
        return findByUserIdAndIsDeleteOrderByCreateTimeDesc(userId, 0);
    }

    /**
     * 查询用户指定状态的会话
     */
    List<QuizSession> findByUserIdAndStatusAndIsDelete(Long userId, QuizStatus status, Integer isDelete);

    /**
     * 查询用户指定状态的会话 (默认未删除)
     */
    default List<QuizSession> findActiveByUserIdAndStatus(Long userId, QuizStatus status) {
        return findByUserIdAndStatusAndIsDelete(userId, status, 0);
    }

    /**
     * 分页查询用户的会话
     */
    Page<QuizSession> findByUserIdAndIsDelete(Long userId, Integer isDelete, Pageable pageable);

    /**
     * 分页查询用户的会话 (默认未删除)
     */
    default Page<QuizSession> findActiveByUserId(Long userId, Pageable pageable) {
        return findByUserIdAndIsDelete(userId, 0, pageable);
    }

    // ==================== 关联查询 (避免 N+1) ====================

    /**
     * 查询会话及其关联的题目
     */
    @EntityGraph(attributePaths = { "questions" })
    Optional<QuizSession> findWithQuestionsById(UUID id);

    /**
     * 查询会话及其关联的回答记录
     */
    @EntityGraph(attributePaths = { "responses" })
    Optional<QuizSession> findWithResponsesById(UUID id);

    /**
     * 查询会话及其所有关联数据
     * 注意: 由于 Hibernate 不支持同时 fetch 多个 List 集合，此方法会抛出 MultipleBagFetchException
     * 建议: 使用 findActiveById + 分别查询 questions 和 responses
     * @deprecated 使用 findActiveById 并分别查询集合
     */
    @Deprecated
    @EntityGraph(attributePaths = { "questions", "responses" })
    Optional<QuizSession> findWithAllDetailsById(UUID id);

    /**
     * 使用 JPQL JOIN FETCH 查询会话及题目
     */
    @Query("SELECT s FROM QuizSession s " +
            "LEFT JOIN FETCH s.questions " +
            "WHERE s.userId = :userId AND s.isDelete = 0 " +
            "ORDER BY s.createTime DESC")
    List<QuizSession> findByUserIdWithQuestions(@Param("userId") Long userId);

    // ==================== 统计查询 ====================

    /**
     * 统计用户各状态的会话数量
     */
    @Query(value = """
            SELECT status, COUNT(*) as count
            FROM quiz_session
            WHERE user_id = :userId AND is_delete = 0
            GROUP BY status
            """, nativeQuery = true)
    List<Object[]> countByStatusForUser(@Param("userId") Long userId);

    /**
     * 统计用户的总会话数
     */
    long countByUserIdAndIsDelete(Long userId, Integer isDelete);

    /**
     * 统计用户的总会话数 (默认未删除)
     */
    default long countActiveByUserId(Long userId) {
        return countByUserIdAndIsDelete(userId, 0);
    }

    /**
     * 统计用户指定状态的会话数
     */
    long countByUserIdAndStatusAndIsDelete(Long userId, QuizStatus status, Integer isDelete);

    /**
     * 统计用户已完成的会话数
     */
    default long countCompletedByUserId(Long userId) {
        return countByUserIdAndStatusAndIsDelete(userId, QuizStatus.COMPLETED, 0);
    }

    /**
     * 统计用户进行中的会话数
     */
    default long countInProgressByUserId(Long userId) {
        return countByUserIdAndStatusAndIsDelete(userId, QuizStatus.IN_PROGRESS, 0);
    }

    // ==================== 复杂查询 ====================

    /**
     * 查询用户在指定文档范围内的会话
     */
    @Query(value = """
            SELECT * FROM quiz_session s
            WHERE s.user_id = :userId
            AND s.is_delete = 0
            AND s.document_scope @> :documentId::jsonb
            ORDER BY s.create_time DESC
            """, nativeQuery = true)
    List<QuizSession> findByUserIdAndDocumentScope(
            @Param("userId") Long userId,
            @Param("documentId") String documentId);

    /**
     * 查询用户最近的 N 个会话
     */
    @Query("SELECT s FROM QuizSession s " +
            "WHERE s.userId = :userId AND s.isDelete = 0 " +
            "ORDER BY s.createTime DESC")
    List<QuizSession> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 查询租户的所有会话
     */
    List<QuizSession> findByTenantIdAndIsDeleteOrderByCreateTimeDesc(Long tenantId, Integer isDelete);

    /**
     * 检查用户是否有进行中的会话
     */
    boolean existsByUserIdAndStatusAndIsDelete(Long userId, QuizStatus status, Integer isDelete);

    /**
     * 检查用户是否有进行中的会话 (默认)
     */
    default boolean hasInProgressSession(Long userId) {
        return existsByUserIdAndStatusAndIsDelete(userId, QuizStatus.IN_PROGRESS, 0);
    }
}
