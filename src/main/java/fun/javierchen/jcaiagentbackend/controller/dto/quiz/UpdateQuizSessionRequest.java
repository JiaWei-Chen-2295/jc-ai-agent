package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新测验会话请求
 *
 * @author JavierChen
 */
@Data
@Schema(description = "更新测验会话请求")
public class UpdateQuizSessionRequest {

    @Schema(description = "操作类型", example = "PAUSE")
    @NotNull(message = "操作类型不能为空")
    private SessionAction action;

    /**
     * 会话操作类型
     */
    public enum SessionAction {
        PAUSE, // 暂停
        RESUME, // 恢复
        ABANDON // 放弃
    }
}
