package fun.javierchen.jcaiagentbackend.controller.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 会话执行概览视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "会话执行概览")
public class ExecutionOverviewVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "总迭代次数")
    private Integer totalIterations;

    @Schema(description = "总日志条数")
    private Long totalLogCount;

    @Schema(description = "总执行耗时 (毫秒)")
    private Long totalExecutionTimeMs;

    @Schema(description = "平均执行耗时 (毫秒)")
    private Double avgExecutionTimeMs;

    @Schema(description = "各阶段数量统计")
    private List<PhaseCount> phaseCounts;

    @Schema(description = "超时日志数量")
    private Long timeoutLogCount;

    @Schema(description = "超时日志列表")
    private List<TimeoutLogInfo> timeoutLogs;

    /**
     * 阶段数量统计
     */
    @Data
    @Builder
    public static class PhaseCount {

        @Schema(description = "阶段名称")
        private String phase;

        @Schema(description = "数量")
        private Long count;
    }

    /**
     * 超时日志简要信息
     */
    @Data
    @Builder
    public static class TimeoutLogInfo {

        @Schema(description = "日志ID")
        private UUID id;

        @Schema(description = "迭代次数")
        private Integer iteration;

        @Schema(description = "阶段")
        private String phase;

        @Schema(description = "工具名称")
        private String toolName;

        @Schema(description = "执行耗时 (毫秒)")
        private Integer executionTimeMs;
    }
}
