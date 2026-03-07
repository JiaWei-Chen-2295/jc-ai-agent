package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.AgentPhase;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.AgentExecutionLog;
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
 * Agent 执行日志 Repository
 *
 * @author JavierChen
 */
public interface AgentExecutionLogRepository extends JpaRepository<AgentExecutionLog, UUID>,
        JpaSpecificationExecutor<AgentExecutionLog> {

    // ==================== 基本查询 ====================

    /**
     * 根据ID查询未删除的日志
     */
    Optional<AgentExecutionLog> findByIdAndIsDelete(UUID id, Integer isDelete);

    /**
     * 根据ID查询未删除的日志 (默认)
     */
    default Optional<AgentExecutionLog> findActiveById(UUID id) {
        return findByIdAndIsDelete(id, 0);
    }

    /**
     * 查询会话的所有执行日志 (按迭代次数和阶段排序)
     */
    List<AgentExecutionLog> findBySessionIdAndIsDeleteOrderByIterationAscPhaseAsc(
            UUID sessionId, Integer isDelete);

    /**
     * 查询会话的所有执行日志 (默认)
     */
    default List<AgentExecutionLog> findActiveBySessionId(UUID sessionId) {
        return findBySessionIdAndIsDeleteOrderByIterationAscPhaseAsc(sessionId, 0);
    }

    /**
     * 查询会话指定迭代的日志
     */
    List<AgentExecutionLog> findBySessionIdAndIterationAndIsDelete(
            UUID sessionId, Integer iteration, Integer isDelete);

    /**
     * 查询会话指定迭代的日志 (默认)
     */
    default List<AgentExecutionLog> findActiveBySessionIdAndIteration(UUID sessionId, Integer iteration) {
        return findBySessionIdAndIterationAndIsDelete(sessionId, iteration, 0);
    }

    /**
     * 查询会话指定阶段的日志
     */
    List<AgentExecutionLog> findBySessionIdAndPhaseAndIsDelete(
            UUID sessionId, AgentPhase phase, Integer isDelete);

    /**
     * 查询会话的 THOUGHT 阶段日志
     */
    default List<AgentExecutionLog> findThoughtsBySessionId(UUID sessionId) {
        return findBySessionIdAndPhaseAndIsDelete(sessionId, AgentPhase.THOUGHT, 0);
    }

    /**
     * 查询会话的 ACTION 阶段日志
     */
    default List<AgentExecutionLog> findActionsBySessionId(UUID sessionId) {
        return findBySessionIdAndPhaseAndIsDelete(sessionId, AgentPhase.ACTION, 0);
    }

    /**
     * 查询会话的 OBSERVATION 阶段日志
     */
    default List<AgentExecutionLog> findObservationsBySessionId(UUID sessionId) {
        return findBySessionIdAndPhaseAndIsDelete(sessionId, AgentPhase.OBSERVATION, 0);
    }

    // ==================== 按工具名称查询 ====================

    /**
     * 查询会话中特定工具的执行日志
     */
    List<AgentExecutionLog> findBySessionIdAndToolNameAndIsDelete(
            UUID sessionId, String toolName, Integer isDelete);

    /**
     * 查询会话中特定工具的执行日志 (默认)
     */
    default List<AgentExecutionLog> findActiveBySessionIdAndToolName(UUID sessionId, String toolName) {
        return findBySessionIdAndToolNameAndIsDelete(sessionId, toolName, 0);
    }

    /**
     * 查询所有使用过的工具名称
     */
    @Query("SELECT DISTINCT log.toolName FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId " +
            "AND log.toolName IS NOT NULL " +
            "AND log.isDelete = 0")
    List<String> findDistinctToolNamesBySessionId(@Param("sessionId") UUID sessionId);

    // ==================== 统计查询 ====================

    /**
     * 统计会话的日志条数
     */
    long countBySessionIdAndIsDelete(UUID sessionId, Integer isDelete);

    /**
     * 统计会话的日志条数 (默认)
     */
    default long countActiveBySessionId(UUID sessionId) {
        return countBySessionIdAndIsDelete(sessionId, 0);
    }

    /**
     * 统计会话的迭代次数
     */
    @Query("SELECT COALESCE(MAX(log.iteration), 0) FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId AND log.isDelete = 0")
    Integer findMaxIterationBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 按阶段统计会话的日志
     */
    @Query(value = """
            SELECT phase, COUNT(*) as count
            FROM agent_execution_log
            WHERE session_id = :sessionId AND is_delete = 0
            GROUP BY phase
            """, nativeQuery = true)
    List<Object[]> countByPhaseForSession(@Param("sessionId") UUID sessionId);

    /**
     * 按工具统计会话的调用次数
     */
    @Query(value = """
            SELECT tool_name, COUNT(*) as count
            FROM agent_execution_log
            WHERE session_id = :sessionId
            AND tool_name IS NOT NULL
            AND is_delete = 0
            GROUP BY tool_name
            ORDER BY count DESC
            """, nativeQuery = true)
    List<Object[]> countByToolNameForSession(@Param("sessionId") UUID sessionId);

    /**
     * 计算会话的平均执行时间
     */
    @Query("SELECT AVG(log.executionTimeMs) FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId " +
            "AND log.executionTimeMs IS NOT NULL " +
            "AND log.isDelete = 0")
    Double findAvgExecutionTimeBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 计算会话的总执行时间
     */
    @Query("SELECT SUM(log.executionTimeMs) FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId " +
            "AND log.executionTimeMs IS NOT NULL " +
            "AND log.isDelete = 0")
    Long findTotalExecutionTimeBySessionId(@Param("sessionId") UUID sessionId);

    // ==================== 超时检测 ====================

    /**
     * 查询超时的执行日志 (超过 5 秒)
     */
    @Query("SELECT log FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId " +
            "AND log.executionTimeMs > :thresholdMs " +
            "AND log.isDelete = 0")
    List<AgentExecutionLog> findTimeoutLogsBySessionId(
            @Param("sessionId") UUID sessionId,
            @Param("thresholdMs") Integer thresholdMs);

    /**
     * 查询超时的执行日志 (默认阈值 5000ms)
     */
    default List<AgentExecutionLog> findTimeoutLogsBySessionId(UUID sessionId) {
        return findTimeoutLogsBySessionId(sessionId, 5000);
    }

    // ==================== 分页查询 ====================

    /**
     * 分页查询会话的日志
     */
    Page<AgentExecutionLog> findBySessionIdAndIsDelete(UUID sessionId, Integer isDelete, Pageable pageable);

    /**
     * 分页查询会话的日志 (默认)
     */
    default Page<AgentExecutionLog> findActiveBySessionId(UUID sessionId, Pageable pageable) {
        return findBySessionIdAndIsDelete(sessionId, 0, pageable);
    }

    /**
     * 分页查询租户的所有日志
     */
    Page<AgentExecutionLog> findByTenantIdAndIsDelete(Long tenantId, Integer isDelete, Pageable pageable);

    /**
     * 查询会话最后一次迭代的日志
     */
    @Query("SELECT log FROM AgentExecutionLog log " +
            "WHERE log.session.id = :sessionId " +
            "AND log.iteration = (SELECT MAX(l.iteration) FROM AgentExecutionLog l " +
            "                     WHERE l.session.id = :sessionId AND l.isDelete = 0) " +
            "AND log.isDelete = 0 " +
            "ORDER BY log.phase")
    List<AgentExecutionLog> findLastIterationBySessionId(@Param("sessionId") UUID sessionId);
}
