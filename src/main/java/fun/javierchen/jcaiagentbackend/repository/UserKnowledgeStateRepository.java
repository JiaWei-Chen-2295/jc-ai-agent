package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
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
 * 用户知识状态 Repository
 * 支持三维认知模型的查询
 *
 * @author JavierChen
 */
public interface UserKnowledgeStateRepository extends JpaRepository<UserKnowledgeState, UUID>,
        JpaSpecificationExecutor<UserKnowledgeState> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的知识状态
     */
    Optional<UserKnowledgeState> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的知识状态 (默认)
     */
    default Optional<UserKnowledgeState> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询用户的所有知识状态
     */
    List<UserKnowledgeState> findByUserIdAndIsDelete(Long userId, Integer isDelete);

    /**
     * 查询用户的所有知识状态 (默认)
     */
    default List<UserKnowledgeState> findActiveByUserId(Long userId) {
        return findByUserIdAndIsDelete(userId, 0);
    }

    /**
     * 查询用户特定主题的知识状态 (唯一约束)
     */
    Optional<UserKnowledgeState> findByTenantIdAndUserIdAndTopicTypeAndTopicIdAndIsDelete(
            Long tenantId, Long userId, TopicType topicType, String topicId, Integer isDelete);

    /**
     * 查询用户特定主题的知识状态 (默认)
     */
    default Optional<UserKnowledgeState> findActiveByUserAndTopic(
            Long tenantId, Long userId, TopicType topicType, String topicId) {
        return findByTenantIdAndUserIdAndTopicTypeAndTopicIdAndIsDelete(
                tenantId, userId, topicType, topicId, 0);
    }

    /**
     * 查询用户指定类型的知识状态
     */
    List<UserKnowledgeState> findByUserIdAndTopicTypeAndIsDelete(
            Long userId, TopicType topicType, Integer isDelete);

    // ==================== 三维认知模型查询 ====================

    /**
     * 查询用户已掌握的知识点 (D ≥ 70 AND L ≤ 40 AND S ≥ 70)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND s.understandingDepth >= 70 " +
            "AND s.cognitiveLoadScore <= 40 " +
            "AND s.stabilityScore >= 70 " +
            "AND s.isDelete = 0")
    List<UserKnowledgeState> findMasteredByUserId(@Param("userId") Long userId);

    /**
     * 查询用户未掌握的知识点 (任一维度不达标)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND (s.understandingDepth < 70 OR s.cognitiveLoadScore > 40 OR s.stabilityScore < 70) " +
            "AND s.isDelete = 0")
    List<UserKnowledgeState> findUnmasteredByUserId(@Param("userId") Long userId);

    /**
     * 查询用户挣扎的知识点 (高负荷 + 低稳定性)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND s.cognitiveLoadScore > 60 " +
            "AND s.stabilityScore < 50 " +
            "AND s.isDelete = 0")
    List<UserKnowledgeState> findStrugglingByUserId(@Param("userId") Long userId);

    /**
     * 查询可以提高难度的知识点 (低负荷 + 高深度)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND s.cognitiveLoadScore < 30 " +
            "AND s.understandingDepth >= 70 " +
            "AND s.isDelete = 0")
    List<UserKnowledgeState> findMasteryReadyForChallenge(@Param("userId") Long userId);

    /**
     * 按理解深度排序查询 (最高到最低)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0 " +
            "ORDER BY s.understandingDepth DESC")
    List<UserKnowledgeState> findByUserIdOrderByDepthDesc(@Param("userId") Long userId);

    /**
     * 按稳定性排序查询 (最低到最高，用于找出最不稳定的知识点)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0 " +
            "ORDER BY s.stabilityScore ASC")
    List<UserKnowledgeState> findByUserIdOrderByStabilityAsc(@Param("userId") Long userId);

    /**
     * 按认知负荷排序查询 (最高到最低，用于找出最吃力的知识点)
     */
    @Query("SELECT s FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0 " +
            "ORDER BY s.cognitiveLoadScore DESC")
    List<UserKnowledgeState> findByUserIdOrderByLoadDesc(@Param("userId") Long userId);

    // ==================== 统计查询 ====================

    /**
     * 统计用户已掌握的知识点数量
     */
    @Query("SELECT COUNT(s) FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND s.understandingDepth >= 70 " +
            "AND s.cognitiveLoadScore <= 40 " +
            "AND s.stabilityScore >= 70 " +
            "AND s.isDelete = 0")
    Long countMasteredByUserId(@Param("userId") Long userId);

    /**
     * 统计用户未掌握的知识点数量
     */
    @Query("SELECT COUNT(s) FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId " +
            "AND (s.understandingDepth < 70 OR s.cognitiveLoadScore > 40 OR s.stabilityScore < 70) " +
            "AND s.isDelete = 0")
    Long countUnmasteredByUserId(@Param("userId") Long userId);

    /**
     * 统计用户的总知识点数量
     */
    long countByUserIdAndIsDelete(Long userId, Integer isDelete);

    /**
     * 统计用户的总知识点数量 (默认)
     */
    default long countActiveByUserId(Long userId) {
        return countByUserIdAndIsDelete(userId, 0);
    }

    /**
     * 计算用户的平均理解深度
     */
    @Query("SELECT AVG(s.understandingDepth) FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0")
    Double findAvgDepthByUserId(@Param("userId") Long userId);

    /**
     * 计算用户的平均认知负荷
     */
    @Query("SELECT AVG(s.cognitiveLoadScore) FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0")
    Double findAvgLoadByUserId(@Param("userId") Long userId);

    /**
     * 计算用户的平均稳定性
     */
    @Query("SELECT AVG(s.stabilityScore) FROM UserKnowledgeState s " +
            "WHERE s.userId = :userId AND s.isDelete = 0")
    Double findAvgStabilityByUserId(@Param("userId") Long userId);

    // ==================== 分页查询 ====================

    /**
     * 分页查询用户的知识状态
     */
    Page<UserKnowledgeState> findByUserIdAndIsDelete(Long userId, Integer isDelete, Pageable pageable);

    /**
     * 分页查询用户的知识状态 (默认)
     */
    default Page<UserKnowledgeState> findActiveByUserId(Long userId, Pageable pageable) {
        return findByUserIdAndIsDelete(userId, 0, pageable);
    }

    /**
     * 判断用户是否存在指定主题的知识状态
     */
    boolean existsByTenantIdAndUserIdAndTopicTypeAndTopicIdAndIsDelete(
            Long tenantId, Long userId, TopicType topicType, String topicId, Integer isDelete);

    /**
     * 判断用户是否存在指定主题的知识状态 (默认)
     */
    default boolean existsActiveByUserAndTopic(Long tenantId, Long userId, TopicType topicType, String topicId) {
        return existsByTenantIdAndUserIdAndTopicTypeAndTopicIdAndIsDelete(
                tenantId, userId, topicType, topicId, 0);
    }
}
