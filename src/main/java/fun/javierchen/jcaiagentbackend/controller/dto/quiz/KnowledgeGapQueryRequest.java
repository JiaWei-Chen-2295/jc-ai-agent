package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识缺口查询请求
 *
 * @author JavierChen
 */
@Data
@Schema(description = "知识缺口查询请求")
public class KnowledgeGapQueryRequest {

    @Schema(description = "状态筛选")
    private KnowledgeGapStatus status;

    @Schema(description = "严重程度筛选")
    private Severity severity;

    @Schema(description = "页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", defaultValue = "10")
    private Integer pageSize = 10;
}
