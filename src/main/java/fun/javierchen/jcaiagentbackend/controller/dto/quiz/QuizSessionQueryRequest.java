package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 测验会话查询请求
 *
 * @author JavierChen
 */
@Data
@Schema(description = "测验会话查询请求")
public class QuizSessionQueryRequest {

    @Schema(description = "会话状态筛选")
    private QuizStatus status;

    @Schema(description = "页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", defaultValue = "10")
    private Integer pageSize = 10;
}
