package fun.javierchen.jcaiagentbackend.controller.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工具调用统计视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "工具调用统计")
public class ToolStatsVO {

    @Schema(description = "工具统计列表")
    private List<ToolStatItem> tools;

    /**
     * 单个工具统计
     */
    @Data
    @Builder
    public static class ToolStatItem {

        @Schema(description = "工具名称")
        private String toolName;

        @Schema(description = "调用次数")
        private Long callCount;

        @Schema(description = "平均执行耗时 (毫秒)")
        private Double avgExecutionTimeMs;

        @Schema(description = "总执行耗时 (毫秒)")
        private Long totalExecutionTimeMs;

        @Schema(description = "最大执行耗时 (毫秒)")
        private Integer maxExecutionTimeMs;

        @Schema(description = "最小执行耗时 (毫秒)")
        private Integer minExecutionTimeMs;
    }
}
