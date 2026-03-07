package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 提交答案响应
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "提交答案响应")
public class SubmitAnswerResponse {

    @Schema(description = "是否正确")
    private Boolean isCorrect;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "正确答案")
    private String correctAnswer;

    @Schema(description = "答案解释")
    private String explanation;

    @Schema(description = "反馈信息")
    private String feedback;

    @Schema(description = "概念掌握程度")
    private String conceptMastery;

    @Schema(description = "是否有下一题")
    private Boolean hasNextQuestion;

    @Schema(description = "下一题信息")
    private QuestionVO nextQuestion;

    @Schema(description = "是否测验结束")
    private Boolean quizCompleted;

    @Schema(description = "当前总分")
    private Integer totalScore;

    @Schema(description = "当前题号")
    private Integer currentQuestionNo;

    @Schema(description = "总题目数")
    private Integer totalQuestions;
}
