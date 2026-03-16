package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 知识覆盖率视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "知识覆盖率")
public class KnowledgeCoverageVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "概念总数（概念清单中的总量）")
    private Integer totalConcepts;

    @Schema(description = "已测概念数（至少答过一题的概念）")
    private Integer testedConcepts;

    @Schema(description = "已掌握概念数（D≥70, L≤40, S≥70）")
    private Integer masteredConcepts;

    @Schema(description = "覆盖率百分比 (已测/总数 * 100)")
    private Double coveragePercent;

    @Schema(description = "掌握率百分比 (已掌握/总数 * 100)")
    private Double masteryPercent;

    @Schema(description = "已答题总数")
    private Integer answeredQuestions;

    @Schema(description = "概念来源", example = "REDIS / AGENT_STATE / CHUNK_ESTIMATE")
    private String conceptSource;

    @Schema(description = "各概念详情")
    private List<ConceptDetail> concepts;

    @Data
    @Builder
    @Schema(description = "单个概念的覆盖详情")
    public static class ConceptDetail {

        @Schema(description = "概念名称")
        private String name;

        @Schema(description = "状态: UNTESTED / TESTING / MASTERED", example = "TESTING")
        private String status;

        @Schema(description = "理解深度 (0-100)，未测时为 null")
        private Integer understandingDepth;

        @Schema(description = "认知负荷 (0-100)，未测时为 null")
        private Integer cognitiveLoad;

        @Schema(description = "稳定性 (0-100)，未测时为 null")
        private Integer stability;

        @Schema(description = "该概念的答题数")
        private Integer questionCount;
    }
}
