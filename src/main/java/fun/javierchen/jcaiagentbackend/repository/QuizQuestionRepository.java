package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizQuestion;
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
 * 测验题目 Repository
 *
 * @author JavierChen
 */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID>,
        JpaSpecificationExecutor<QuizQuestion> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的题目
     */
    Optional<QuizQuestion> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的题目 (默认)
     */
    default Optional<QuizQuestion> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询会话的所有题目 (按题号排序)
     */
    List<QuizQuestion> findBySessionIdAndIsDeleteOrderByQuestionNoAsc(UUID sessionId, Integer isDelete);

    /**
     * 查询会话的所有题目 (默认未删除)
     */
    default List<QuizQuestion> findActiveBySessionId(UUID sessionId) {
        return findBySessionIdAndIsDeleteOrderByQuestionNoAsc(sessionId, 0);
    }

    /**
     * 查询会话指定题号的题目
     */
    Optional<QuizQuestion> findBySessionIdAndQuestionNoAndIsDelete(
            UUID sessionId, Integer questionNo, Integer isDelete);

    /**
     * 查询会话指定题号的题目 (默认)
     */
    default Optional<QuizQuestion> findActiveBySessionIdAndQuestionNo(UUID sessionId, Integer questionNo) {
        return findBySessionIdAndQuestionNoAndIsDelete(sessionId, questionNo, 0);
    }

    // ==================== 按类型/难度查询 ====================

    /**
     * 查询指定类型的题目
     */
    List<QuizQuestion> findBySessionIdAndQuestionTypeAndIsDelete(
            UUID sessionId, QuestionType questionType, Integer isDelete);

    /**
     * 查询指定难度的题目
     */
    List<QuizQuestion> findBySessionIdAndDifficultyAndIsDelete(
            UUID sessionId, Difficulty difficulty, Integer isDelete);

    /**
     * 查询指定类型和难度的题目
     */
    List<QuizQuestion> findBySessionIdAndQuestionTypeAndDifficultyAndIsDelete(
            UUID sessionId, QuestionType questionType, Difficulty difficulty, Integer isDelete);

    // ==================== 按知识点查询 ====================

    /**
     * 查询关联特定知识点的题目
     */
    List<QuizQuestion> findByRelatedConceptAndIsDelete(String relatedConcept, Integer isDelete);

    /**
     * 查询关联特定知识点的题目 (默认)
     */
    default List<QuizQuestion> findActiveByRelatedConcept(String relatedConcept) {
        return findByRelatedConceptAndIsDelete(relatedConcept, 0);
    }

    /**
     * 模糊查询知识点
     */
    @Query("SELECT q FROM QuizQuestion q " +
            "WHERE q.relatedConcept LIKE %:concept% AND q.isDelete = 0")
    List<QuizQuestion> findByRelatedConceptContaining(@Param("concept") String concept);

    /**
     * 查询会话中关联特定知识点的题目
     */
    @Query("SELECT q FROM QuizQuestion q " +
            "WHERE q.session.id = :sessionId " +
            "AND q.relatedConcept = :concept " +
            "AND q.isDelete = 0")
    List<QuizQuestion> findBySessionIdAndRelatedConcept(
            @Param("sessionId") UUID sessionId,
            @Param("concept") String concept);

    // ==================== 统计查询 ====================

    /**
     * 统计会话的题目数量
     */
    long countBySessionIdAndIsDelete(UUID sessionId, Integer isDelete);

    /**
     * 统计会话的题目数量 (默认)
     */
    default long countActiveBySessionId(UUID sessionId) {
        return countBySessionIdAndIsDelete(sessionId, 0);
    }

    /**
     * 统计会话中各难度的题目数量
     */
    @Query(value = """
            SELECT difficulty, COUNT(*) as count
            FROM quiz_question
            WHERE session_id = :sessionId AND is_delete = 0
            GROUP BY difficulty
            """, nativeQuery = true)
    List<Object[]> countByDifficultyForSession(@Param("sessionId") UUID sessionId);

    /**
     * 统计会话中各类型的题目数量
     */
    @Query(value = """
            SELECT question_type, COUNT(*) as count
            FROM quiz_question
            WHERE session_id = :sessionId AND is_delete = 0
            GROUP BY question_type
            """, nativeQuery = true)
    List<Object[]> countByTypeForSession(@Param("sessionId") UUID sessionId);

    /**
     * 获取会话中的最大题号
     */
    @Query("SELECT COALESCE(MAX(q.questionNo), 0) FROM QuizQuestion q " +
            "WHERE q.session.id = :sessionId AND q.isDelete = 0")
    Integer findMaxQuestionNoBySessionId(@Param("sessionId") UUID sessionId);

    // ==================== 来源查询 ====================

    /**
     * 查询来自特定文档的题目
     */
    List<QuizQuestion> findBySourceDocIdAndIsDelete(Long sourceDocId, Integer isDelete);

    /**
     * 查询来自特定向量块的题目
     */
    Optional<QuizQuestion> findBySourceChunkIdAndIsDelete(UUID sourceChunkId, Integer isDelete);

    /**
     * 分页查询租户的所有题目
     */
    Page<QuizQuestion> findByTenantIdAndIsDelete(Long tenantId, Integer isDelete, Pageable pageable);

    // ==================== 复杂查询 ====================

    /**
     * 查询用户做过的所有题目 (通过会话关联)
     */
    @Query("SELECT q FROM QuizQuestion q " +
            "JOIN q.session s " +
            "WHERE s.userId = :userId AND q.isDelete = 0 " +
            "ORDER BY q.createTime DESC")
    List<QuizQuestion> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 查询特定知识点的题目数量
     */
    @Query("SELECT q.relatedConcept, COUNT(q) FROM QuizQuestion q " +
            "WHERE q.session.userId = :userId AND q.isDelete = 0 " +
            "GROUP BY q.relatedConcept " +
            "ORDER BY COUNT(q) DESC")
    List<Object[]> countByConceptForUser(@Param("userId") Long userId);
}
