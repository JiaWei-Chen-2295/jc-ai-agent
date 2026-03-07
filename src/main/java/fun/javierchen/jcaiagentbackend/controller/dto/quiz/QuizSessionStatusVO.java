package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 测验会话状态视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "测验会话状态")
public class QuizSessionStatusVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "会话状态")
    private QuizStatus status;

    @Schema(description = "当前题号")
    private Integer currentQuestionNo;

    @Schema(description = "总题目数")
    private Integer totalQuestions;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "正确率")
    private Double accuracy;

    @Schema(description = "是否可以继续")
    private Boolean canContinue;

    @Schema(description = "当前题目 (如有)")
    private QuestionVO currentQuestion;
}
