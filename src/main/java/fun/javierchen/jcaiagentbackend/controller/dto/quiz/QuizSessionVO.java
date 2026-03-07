package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 测验会话视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "测验会话信息")
public class QuizSessionVO {

    @Schema(description = "会话ID")
    private UUID sessionId;

    @Schema(description = "测验模式")
    private QuizMode quizMode;

    @Schema(description = "会话状态")
    private QuizStatus status;

    @Schema(description = "当前题号")
    private Integer currentQuestionNo;

    @Schema(description = "总题目数")
    private Integer totalQuestions;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "第一道题目 (创建时返回)")
    private QuestionVO firstQuestion;
}
