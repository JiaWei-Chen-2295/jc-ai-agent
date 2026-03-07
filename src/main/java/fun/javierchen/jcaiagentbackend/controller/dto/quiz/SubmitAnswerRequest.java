package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 提交答案请求
 *
 * @author JavierChen
 */
@Data
@Schema(description = "提交答案请求")
public class SubmitAnswerRequest {

    @Schema(description = "题目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "题目ID不能为空")
    private UUID questionId;

    @Schema(description = "用户答案", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "答案不能为空")
    private String answer;

    @Schema(description = "响应时间 (毫秒)")
    private Integer responseTimeMs;
}
