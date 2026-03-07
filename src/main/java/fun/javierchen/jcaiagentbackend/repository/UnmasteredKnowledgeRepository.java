package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.GapType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Severity;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UnmasteredKnowledge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 未掌握知识 (知识缺口) Repository
 *
 * @author JavierChen
 */
public interface UnmasteredKnowledgeRepository extends JpaRepository<UnmasteredKnowledge, UUID>,
        JpaSpecificationExecutor<UnmasteredKnowledge> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的知识缺口
     */
    Optional<UnmasteredKnowledge> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的知识缺口 (默认)
     */
    default Optional<UnmasteredKnowledge> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询用户的所有知识缺口
     */
    List<UnmasteredKnowledge> findByUserIdAndIsDeleteOrderByCreateTimeDesc(Long userId, Integer isDelete);

    /**
     * 查询用户的所有知识缺口 (默认)
     */
    default List<UnmasteredKnowledge> findActiveByUserId(Long userId) {
        return findByUserIdAndIsDeleteOrderByCreateTimeDesc(userId, 0);
    }

    /**
     * 查询用户指定状态的知识缺口
     */
    List<UnmasteredKnowledge> findByUserIdAndStatusAndIsDelete(
            Long userId, KnowledgeGapStatus status, Integer isDelete);

    /**
     * 查询用户的活跃知识缺口
     */
    default List<UnmasteredKnowledge> findActiveGapsByUserId(Long userId) {
        return findByUserIdAndStatusAndIsDelete(userId, KnowledgeGapStatus.ACTIVE, 0);
    }

    /**
     * 查询用户已解决的知识缺口
     */
    default List<UnmasteredKnowledge> findResolvedGapsByUserId(Long userId) {
        return findByUserIdAndStatusAndIsDelete(userId, KnowledgeGapStatus.RESOLVED, 0);
    }

    /**
     * 查询用户特定概念的知识缺口
     */
    Optional<UnmasteredKnowledge> findByUserIdAndConceptNameAndIsDelete(
            Long userId, String conceptName, Integer isDelete);

    /**
     * 查询用户特定概念的知识缺口 (默认)
     */
    default Optional<UnmasteredKnowledge> findActiveByUserIdAndConcept(Long userId, String conceptName) {
        return findByUserIdAndConceptNameAndIsDelete(userId, conceptName, 0);
    }

    // ==================== 按严重程度查询 ====================

    /**
     * 查询用户指定严重程度的知识缺口
     */
    List<UnmasteredKnowledge> findByUserIdAndSeverityAndIsDelete(
            Long userId, Severity severity, Integer isDelete);

    /**
     * 查询用户的高严重度知识缺口
     */
    default List<UnmasteredKnowledge> findHighSeverityByUserId(Long userId) {
        return findByUserIdAndSeverityAndIsDelete(userId, Severity.HIGH, 0);
    }

    /**
     * 按优先级排序查询 (严重程度 + 失败次数)
     */
    @Query("SELECT u FROM UnmasteredKnowledge u " +
            "WHERE u.userId = :userId " +
            "AND u.status = 'ACTIVE' " +
            "AND u.isDelete = 0 " +
            "ORDER BY " +
            "   CASE u.severity " +
            "       WHEN 'HIGH' THEN 3 " +
            "       WHEN 'MEDIUM' THEN 2 " +
            "       WHEN 'LOW' THEN 1 " +
            "       ELSE 0 END DESC, " +
            "   u.failureCount DESC")
    List<UnmasteredKnowledge> findActiveByUserIdOrderByPriority(@Param("userId") Long userId);

    // ==================== 按类型查询 ====================

    /**
     * 查询用户指定类型的知识缺口
     */
    List<UnmasteredKnowledge> findByUserIdAndGapTypeAndIsDelete(
            Long userId, GapType gapType, Integer isDelete);

    /**
     * 查询概念性缺口
     */
    default List<UnmasteredKnowledge> findConceptualGapsByUserId(Long userId) {
        return findByUserIdAndGapTypeAndIsDelete(userId, GapType.CONCEPTUAL, 0);
    }

    /**
     * 查询程序性缺口
     */
    default List<UnmasteredKnowledge> findProceduralGapsByUserId(Long userId) {
        return findByUserIdAndGapTypeAndIsDelete(userId, GapType.PROCEDURAL, 0);
    }

    /**
     * 查询边界性缺口
     */
    default List<UnmasteredKnowledge> findBoundaryGapsByUserId(Long userId) {
        return findByUserIdAndGapTypeAndIsDelete(userId, GapType.BOUNDARY, 0);
    }

    // ==================== 统计查询 ====================

    /**
     * 统计用户的活跃知识缺口数量
     */
    long countByUserIdAndStatusAndIsDelete(Long userId, KnowledgeGapStatus status, Integer isDelete);

    /**
     * 统计用户的活跃知识缺口数量 (默认)
     */
    default long countActiveGapsByUserId(Long userId) {
        return countByUserIdAndStatusAndIsDelete(userId, KnowledgeGapStatus.ACTIVE, 0);
    }

    /**
     * 统计用户已解决的知识缺口数量
     */
    default long countResolvedGapsByUserId(Long userId) {
        return countByUserIdAndStatusAndIsDelete(userId, KnowledgeGapStatus.RESOLVED, 0);
    }

    /**
     * 按严重程度统计用户的知识缺口
     */
    @Query(value = """
            SELECT severity, COUNT(*) as count
            FROM unmastered_knowledge
            WHERE user_id = :userId AND status = 'ACTIVE' AND is_delete = 0
            GROUP BY severity
            """, nativeQuery = true)
    List<Object[]> countBySeverityForUser(@Param("userId") Long userId);

    /**
     * 按类型统计用户的知识缺口
     */
    @Query(value = """
            SELECT gap_type, COUNT(*) as count
            FROM unmastered_knowledge
            WHERE user_id = :userId AND status = 'ACTIVE' AND is_delete = 0
            GROUP BY gap_type
            """, nativeQuery = true)
    List<Object[]> countByTypeForUser(@Param("userId") Long userId);

    /**
     * 计算用户的平均失败次数
     */
    @Query("SELECT AVG(u.failureCount) FROM UnmasteredKnowledge u " +
            "WHERE u.userId = :userId AND u.isDelete = 0")
    Double findAvgFailureCountByUserId(@Param("userId") Long userId);

    // ==================== 来源查询 ====================

    /**
     * 查询来自特定会话的知识缺口
     */
    List<UnmasteredKnowledge> findBySourceSessionIdAndIsDelete(UUID sourceSessionId, Integer isDelete);

    /**
     * 查询来自特定文档的知识缺口
     */
    List<UnmasteredKnowledge> findBySourceDocIdAndIsDelete(Long sourceDocId, Integer isDelete);

    // ==================== 批量更新 ====================

    /**
     * 批量标记为已解决
     */
    @Modifying
    @Transactional
    @Query("UPDATE UnmasteredKnowledge u " +
            "SET u.status = 'RESOLVED', u.resolvedAt = :resolvedAt " +
            "WHERE u.id IN :ids AND u.isDelete = 0")
    int markAsResolved(@Param("ids") List<UUID> ids, @Param("resolvedAt") OffsetDateTime resolvedAt);

    /**
     * 增加失败计数
     */
    @Modifying
    @Transactional
    @Query("UPDATE UnmasteredKnowledge u " +
            "SET u.failureCount = u.failureCount + 1 " +
            "WHERE u.id = :id AND u.isDelete = 0")
    int incrementFailureCount(@Param("id") UUID id);

    // ==================== 分页查询 ====================

    /**
     * 分页查询用户的知识缺口
     */
    Page<UnmasteredKnowledge> findByUserIdAndIsDelete(Long userId, Integer isDelete, Pageable pageable);

    /**
     * 分页查询用户的知识缺口 (默认)
     */
    default Page<UnmasteredKnowledge> findActiveByUserId(Long userId, Pageable pageable) {
        return findByUserIdAndIsDelete(userId, 0, pageable);
    }

    /**
     * 判断用户是否存在特定概念的活跃缺口
     */
    boolean existsByUserIdAndConceptNameAndStatusAndIsDelete(
            Long userId, String conceptName, KnowledgeGapStatus status, Integer isDelete);

    /**
     * 判断用户是否存在特定概念的活跃缺口 (默认)
     */
    default boolean existsActiveGapByUserIdAndConcept(Long userId, String conceptName) {
        return existsByUserIdAndConceptNameAndStatusAndIsDelete(
                userId, conceptName, KnowledgeGapStatus.ACTIVE, 0);
    }
}
