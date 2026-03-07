package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.AgentPhase;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行日志实体
 * 记录 Agent 的 Thought-Action-Observation 循环
 * 用于调试和分析
 *
 * @author JavierChen
 */
@Entity
@Table(name = "agent_execution_log", indexes = {
        @Index(name = "idx_agent_execution_log_session", columnList = "session_id"),
        @Index(name = "idx_agent_execution_log_phase", columnList = "phase")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class AgentExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * ReAct 循环迭代次数
     */
    @Column(name = "iteration", nullable = false)
    private Integer iteration;

    /**
     * ReAct 阶段
     * - THOUGHT: 思考阶段
     * - ACTION: 行动阶段
     * - OBSERVATION: 观察阶段
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 32)
    private AgentPhase phase;

    /**
     * 调用的工具名称
     */
    @Column(name = "tool_name", length = 64)
    private String toolName;

    /**
     * 输入数据 (JSONB -> Map<String, Object>)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "input_data", columnDefinition = "jsonb")
    private Map<String, Object> inputData;

    /**
     * 输出数据 (JSONB -> Map<String, Object>)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "output_data", columnDefinition = "jsonb")
    private Map<String, Object> outputData;

    /**
     * 执行时刻
     */
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    /**
     * 执行耗时 (毫秒)
     */
    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    // ==================== 时间戳 ====================

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;

    // ==================== 关联关系 ====================

    /**
     * 所属会话 (多对一)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private QuizSession session;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建 THOUGHT 阶段日志
     */
    public static AgentExecutionLog thought(QuizSession session, int iteration,
            Map<String, Object> input, Map<String, Object> output) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setSession(session);
        log.setTenantId(session.getTenantId());
        log.setIteration(iteration);
        log.setPhase(AgentPhase.THOUGHT);
        log.setInputData(input);
        log.setOutputData(output);
        return log;
    }

    /**
     * 创建 ACTION 阶段日志
     */
    public static AgentExecutionLog action(QuizSession session, int iteration,
            String toolName, Map<String, Object> input) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setSession(session);
        log.setTenantId(session.getTenantId());
        log.setIteration(iteration);
        log.setPhase(AgentPhase.ACTION);
        log.setToolName(toolName);
        log.setInputData(input);
        return log;
    }

    /**
     * 创建 OBSERVATION 阶段日志
     */
    public static AgentExecutionLog observation(QuizSession session, int iteration,
            Map<String, Object> output, int executionTimeMs) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setSession(session);
        log.setTenantId(session.getTenantId());
        log.setIteration(iteration);
        log.setPhase(AgentPhase.OBSERVATION);
        log.setOutputData(output);
        log.setExecutionTimeMs(executionTimeMs);
        return log;
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为 THOUGHT 阶段
     */
    public boolean isThought() {
        return phase == AgentPhase.THOUGHT;
    }

    /**
     * 判断是否为 ACTION 阶段
     */
    public boolean isAction() {
        return phase == AgentPhase.ACTION;
    }

    /**
     * 判断是否为 OBSERVATION 阶段
     */
    public boolean isObservation() {
        return phase == AgentPhase.OBSERVATION;
    }

    /**
     * 判断执行是否超时 (超过 5 秒)
     */
    public boolean isTimeout() {
        return executionTimeMs != null && executionTimeMs > 5000;
    }
}
