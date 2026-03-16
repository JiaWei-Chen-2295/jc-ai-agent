package fun.javierchen.jcaiagentbackend.controller.dto.agent;

import fun.javierchen.jcaiagentbackend.model.entity.quiz.AgentExecutionLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行日志视图对象
 * 用于列表展示，避免 Entity 循环引用
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "Agent 执行日志")
public class ExecutionLogVO {

    @Schema(description = "日志ID")
    private UUID id;

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "迭代次数")
    private Integer iteration;

    @Schema(description = "阶段: THOUGHT/ACTION/OBSERVATION")
    private String phase;

    @Schema(description = "工具名称")
    private String toolName;

    @Schema(description = "输入数据")
    private Map<String, Object> inputData;

    @Schema(description = "输出数据")
    private Map<String, Object> outputData;

    @Schema(description = "执行耗时 (毫秒)")
    private Integer executionTimeMs;

    @Schema(description = "是否超时")
    private Boolean timeout;

    @Schema(description = "执行时间")
    private LocalDateTime timestamp;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 从 Entity 转换为 VO
     */
    public static ExecutionLogVO fromEntity(AgentExecutionLog log) {
        if (log == null) {
            return null;
        }
        return ExecutionLogVO.builder()
                .id(log.getId())
                .sessionId(log.getSession() != null ? log.getSession().getId() : null)
                .tenantId(log.getTenantId())
                .iteration(log.getIteration())
                .phase(log.getPhase() != null ? log.getPhase().name() : null)
                .toolName(log.getToolName())
                .inputData(log.getInputData())
                .outputData(log.getOutputData())
                .executionTimeMs(log.getExecutionTimeMs())
                .timeout(log.isTimeout())
                .timestamp(log.getTimestamp())
                .createTime(log.getCreateTime())
                .build();
    }
}
