package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 创建测验会话请求
 *
 * @author JavierChen
 */
@Data
@Schema(description = "创建测验会话请求")
public class CreateQuizSessionRequest {

    @Schema(description = "文档ID列表 (为空则使用全部文档)")
    private List<Long> documentIds;

    @Schema(description = "测验模式", defaultValue = "ADAPTIVE")
    private QuizMode quizMode = QuizMode.ADAPTIVE;

    @Schema(description = "题目数量 (0表示自适应)", defaultValue = "0")
    @Min(0)
    @Max(50)
    private Integer questionCount = 0;
}
