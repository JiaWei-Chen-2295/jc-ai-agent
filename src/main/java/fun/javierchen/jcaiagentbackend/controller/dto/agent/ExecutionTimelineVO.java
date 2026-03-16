package fun.javierchen.jcaiagentbackend.controller.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行时间线视图对象
 * 单次会话的完整 ReAct 执行时间线
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "Agent 执行时间线")
public class ExecutionTimelineVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "迭代记录列表，按迭代次数分组")
    private List<IterationRecord> iterations;

    /**
     * 单次迭代记录
     * 包含 THOUGHT → ACTION → OBSERVATION 的完整链路
     */
    @Data
    @Builder
    public static class IterationRecord {

        @Schema(description = "迭代次数")
        private Integer iteration;

        @Schema(description = "思考阶段日志")
        private PhaseLog thought;

        @Schema(description = "行动阶段日志")
        private PhaseLog action;

        @Schema(description = "观察阶段日志")
        private PhaseLog observation;
    }

    /**
     * 阶段日志
     */
    @Data
    @Builder
    public static class PhaseLog {

        @Schema(description = "日志ID")
        private UUID id;

        @Schema(description = "阶段类型: THOUGHT/ACTION/OBSERVATION")
        private String phase;

        @Schema(description = "工具名称 (仅 ACTION 阶段)")
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
    }
}
