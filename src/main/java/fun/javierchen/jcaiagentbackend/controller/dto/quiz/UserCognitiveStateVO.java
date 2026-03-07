package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户认知状态视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "用户认知状态")
public class UserCognitiveStateVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "平均理解深度")
    private Integer avgUnderstandingDepth;

    @Schema(description = "平均认知负荷")
    private Integer avgCognitiveLoad;

    @Schema(description = "平均稳定性")
    private Integer avgStability;

    @Schema(description = "已掌握知识点数")
    private Integer masteredCount;

    @Schema(description = "未掌握知识点数")
    private Integer unmasteredCount;

    @Schema(description = "总知识点数")
    private Integer totalTopics;

    @Schema(description = "总答题数")
    private Long totalAnswers;

    @Schema(description = "正确答题数")
    private Long correctAnswers;

    @Schema(description = "正确率")
    private Double accuracy;

    @Schema(description = "薄弱知识点")
    private List<String> strugglingTopics;

    @Schema(description = "可提高难度的知识点")
    private List<String> readyForChallenge;

    @Schema(description = "学习建议")
    private String recommendation;

    @Schema(description = "是否已达成全部掌握")
    private Boolean masteryAchieved;
}
