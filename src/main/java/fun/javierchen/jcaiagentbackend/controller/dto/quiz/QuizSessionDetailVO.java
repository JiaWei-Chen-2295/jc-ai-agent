package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 测验会话详情视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "测验会话详情")
public class QuizSessionDetailVO {

    @Schema(description = "会话基本信息")
    private QuizSessionVO session;

    @Schema(description = "所有题目列表")
    private List<QuestionVO> questions;

    @Schema(description = "答题记录")
    private List<ResponseVO> responses;

    @Schema(description = "认知分析摘要")
    private CognitiveSummary cognitiveSummary;

    /**
     * 认知分析摘要
     */
    @Data
    @Builder
    public static class CognitiveSummary {
        @Schema(description = "理解深度")
        private Integer understandingDepth;

        @Schema(description = "认知负荷")
        private Integer cognitiveLoad;

        @Schema(description = "稳定性")
        private Integer stability;

        @Schema(description = "正确率")
        private Double accuracy;
    }
}
