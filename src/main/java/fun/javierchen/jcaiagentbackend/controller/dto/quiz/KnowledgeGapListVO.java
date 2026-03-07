package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.GapType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识缺口列表视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "知识缺口列表")
public class KnowledgeGapListVO {

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "活跃缺口数")
    private Long activeCount;

    @Schema(description = "已解决数")
    private Long resolvedCount;

    @Schema(description = "缺口列表")
    private List<KnowledgeGapVO> list;

    /**
     * 知识缺口详情
     */
    @Data
    @Builder
    public static class KnowledgeGapVO {

        @Schema(description = "缺口ID")
        private UUID id;

        @Schema(description = "概念名称")
        private String conceptName;

        @Schema(description = "缺口类型")
        private GapType gapType;

        @Schema(description = "缺口描述")
        private String gapDescription;

        @Schema(description = "根本原因")
        private String rootCause;

        @Schema(description = "严重程度")
        private Severity severity;

        @Schema(description = "状态")
        private KnowledgeGapStatus status;

        @Schema(description = "失败次数")
        private Integer failureCount;

        @Schema(description = "创建时间")
        private LocalDateTime createTime;

        @Schema(description = "解决时间")
        private LocalDateTime resolvedAt;
    }
}
