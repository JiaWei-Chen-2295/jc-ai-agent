package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuestionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 题目回答 Repository
 *
 * @author JavierChen
 */
public interface QuestionResponseRepository extends JpaRepository<QuestionResponse, UUID>,
        JpaSpecificationExecutor<QuestionResponse> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的回答
     */
    Optional<QuestionResponse> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的回答 (默认)
     */
    default Optional<QuestionResponse> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询会话的所有回答
     */
    List<QuestionResponse> findBySessionIdAndIsDeleteOrderByCreateTimeAsc(UUID sessionId, Integer isDelete);

    /**
     * 查询会话的所有回答 (默认)
     */
    default List<QuestionResponse> findActiveBySessionId(UUID sessionId) {
        return findBySessionIdAndIsDeleteOrderByCreateTimeAsc(sessionId, 0);
    }

    /**
     * 查询题目的所有回答
     */
    List<QuestionResponse> findByQuestionIdAndIsDelete(UUID questionId, Integer isDelete);

    /**
     * 查询题目的所有回答 (默认)
     */
    default List<QuestionResponse> findActiveByQuestionId(UUID questionId) {
        return findByQuestionIdAndIsDelete(questionId, 0);
    }

    /**
     * 检查题目是否已被回答
     */
    default boolean existsByQuestionId(UUID questionId) {
        return !findByQuestionIdAndIsDelete(questionId, 0).isEmpty();
    }

    /**
     * 查询用户的所有回答
     */
    List<QuestionResponse> findByUserIdAndIsDeleteOrderByCreateTimeDesc(Long userId, Integer isDelete);

    /**
     * 查询用户的所有回答 (默认)
     */
    default List<QuestionResponse> findActiveByUserId(Long userId) {
        return findByUserIdAndIsDeleteOrderByCreateTimeDesc(userId, 0);
    }

    // ==================== 正确率统计 ====================

    /**
     * 统计会话的正确回答数
     */
    long countBySessionIdAndIsCorrectAndIsDelete(UUID sessionId, Boolean isCorrect, Integer isDelete);

    /**
     * 统计会话的正确回答数 (默认)
     */
    default long countCorrectBySessionId(UUID sessionId) {
        return countBySessionIdAndIsCorrectAndIsDelete(sessionId, true, 0);
    }

    /**
     * 统计会话的总回答数
     */
    long countBySessionIdAndIsDelete(UUID sessionId, Integer isDelete);

    /**
     * 统计会话的总回答数 (默认)
     */
    default long countActiveBySessionId(UUID sessionId) {
        return countBySessionIdAndIsDelete(sessionId, 0);
    }

    /**
     * 统计用户的正确回答数
     */
    long countByUserIdAndIsCorrectAndIsDelete(Long userId, Boolean isCorrect, Integer isDelete);

    /**
     * 统计用户的正确回答数 (默认)
     */
    default long countCorrectByUserId(Long userId) {
        return countByUserIdAndIsCorrectAndIsDelete(userId, true, 0);
    }

    /**
     * 统计用户的总回答数
     */
    long countByUserIdAndIsDelete(Long userId, Integer isDelete);

    /**
     * 统计用户的总回答数 (默认)
     */
    default long countActiveByUserId(Long userId) {
        return countByUserIdAndIsDelete(userId, 0);
    }

    // ==================== 认知分析查询 ====================

    /**
     * 查询会话中检测到犹豫的回答
     */
    List<QuestionResponse> findBySessionIdAndHesitationDetectedAndIsDelete(
            UUID sessionId, Boolean hesitationDetected, Integer isDelete);

    /**
     * 查询会话中检测到困惑的回答
     */
    List<QuestionResponse> findBySessionIdAndConfusionDetectedAndIsDelete(
            UUID sessionId, Boolean confusionDetected, Integer isDelete);

    /**
     * 查询会话中指定掌握程度的回答
     */
    List<QuestionResponse> findBySessionIdAndConceptMasteryAndIsDelete(
            UUID sessionId, ConceptMastery conceptMastery, Integer isDelete);

    /**
     * 计算会话的平均响应时间
     */
    @Query("SELECT AVG(r.responseTimeMs) FROM QuestionResponse r " +
            "WHERE r.session.id = :sessionId AND r.isDelete = 0")
    Double findAvgResponseTimeBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 计算用户的平均响应时间
     */
    @Query("SELECT AVG(r.responseTimeMs) FROM QuestionResponse r " +
            "WHERE r.userId = :userId AND r.isDelete = 0")
    Double findAvgResponseTimeByUserId(@Param("userId") Long userId);

    /**
     * 查询会话中响应时间超过阈值的回答
     */
    @Query("SELECT r FROM QuestionResponse r " +
            "WHERE r.session.id = :sessionId " +
            "AND r.responseTimeMs > :threshold " +
            "AND r.isDelete = 0")
    List<QuestionResponse> findSlowResponsesBySessionId(
            @Param("sessionId") UUID sessionId,
            @Param("threshold") Integer threshold);

    // ==================== 复杂统计 ====================

    /**
     * 按掌握程度统计会话的回答
     */
    @Query(value = """
            SELECT concept_mastery, COUNT(*) as count
            FROM question_response
            WHERE session_id = :sessionId AND is_delete = 0
            GROUP BY concept_mastery
            """, nativeQuery = true)
    List<Object[]> countByMasteryForSession(@Param("sessionId") UUID sessionId);

    /**
     * 查询用户在特定知识点上的回答
     */
    @Query("SELECT r FROM QuestionResponse r " +
            "JOIN r.question q " +
            "WHERE r.userId = :userId " +
            "AND q.relatedConcept = :concept " +
            "AND r.isDelete = 0 " +
            "ORDER BY r.createTime DESC")
    List<QuestionResponse> findByUserIdAndConcept(
            @Param("userId") Long userId,
            @Param("concept") String concept);

    /**
     * 统计用户在特定知识点上的正确率
     */
    @Query("SELECT COUNT(r) FROM QuestionResponse r " +
            "JOIN r.question q " +
            "WHERE r.userId = :userId " +
            "AND q.relatedConcept = :concept " +
            "AND r.isCorrect = true " +
            "AND r.isDelete = 0")
    Long countCorrectByUserIdAndConcept(
            @Param("userId") Long userId,
            @Param("concept") String concept);

    /**
     * 统计用户在特定知识点上的总回答数
     */
    @Query("SELECT COUNT(r) FROM QuestionResponse r " +
            "JOIN r.question q " +
            "WHERE r.userId = :userId " +
            "AND q.relatedConcept = :concept " +
            "AND r.isDelete = 0")
    Long countByUserIdAndConcept(
            @Param("userId") Long userId,
            @Param("concept") String concept);

    /**
     * 分页查询用户的回答历史
     */
    Page<QuestionResponse> findByUserIdAndIsDelete(Long userId, Integer isDelete, Pageable pageable);

    /**
     * 查询用户最近的错误回答
     */
    @Query("SELECT r FROM QuestionResponse r " +
            "WHERE r.userId = :userId " +
            "AND r.isCorrect = false " +
            "AND r.isDelete = 0 " +
            "ORDER BY r.createTime DESC")
    List<QuestionResponse> findRecentWrongAnswersByUserId(
            @Param("userId") Long userId, Pageable pageable);
}
