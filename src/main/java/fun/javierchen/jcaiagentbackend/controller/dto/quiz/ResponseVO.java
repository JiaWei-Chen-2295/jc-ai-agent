package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 答题记录视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "答题记录")
public class ResponseVO {

    @Schema(description = "记录ID")
    private UUID id;

    @Schema(description = "题目ID")
    private UUID questionId;

    @Schema(description = "用户答案")
    private String userAnswer;

    @Schema(description = "是否正确")
    private Boolean isCorrect;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "响应时间 (毫秒)")
    private Integer responseTimeMs;

    @Schema(description = "概念掌握程度")
    private ConceptMastery conceptMastery;

    @Schema(description = "反馈信息")
    private String feedback;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
