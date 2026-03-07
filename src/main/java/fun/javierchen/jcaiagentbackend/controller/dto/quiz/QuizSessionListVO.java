package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 测验会话列表视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "测验会话列表")
public class QuizSessionListVO {

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "会话列表")
    private List<QuizSessionVO> list;
}
