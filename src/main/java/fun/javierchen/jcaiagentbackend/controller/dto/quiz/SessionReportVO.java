package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话分析报告视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "会话分析报告")
public class SessionReportVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "总题数")
    private Integer totalQuestions;

    @Schema(description = "正确题数")
    private Integer correctCount;

    @Schema(description = "总分")
    private Integer totalScore;

    @Schema(description = "正确率")
    private Double accuracy;

    @Schema(description = "平均响应时间 (毫秒)")
    private Double avgResponseTimeMs;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @Schema(description = "用时 (秒)")
    private Long durationSeconds;

    @Schema(description = "理解深度")
    private Integer understandingDepth;

    @Schema(description = "认知负荷")
    private Integer cognitiveLoad;

    @Schema(description = "稳定性")
    private Integer stability;

    @Schema(description = "知识点分析")
    private List<ConceptAnalysis> conceptAnalyses;

    @Schema(description = "改进建议")
    private List<String> suggestions;

    /**
     * 知识点分析
     */
    @Data
    @Builder
    public static class ConceptAnalysis {
        @Schema(description = "知识点名称")
        private String concept;

        @Schema(description = "题目数")
        private Integer questionCount;

        @Schema(description = "正确数")
        private Integer correctCount;

        @Schema(description = "正确率")
        private Double accuracy;

        @Schema(description = "掌握程度")
        private String mastery;
    }
}
