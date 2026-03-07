package fun.javierchen.jcaiagentbackend.controller.dto.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 题目视图对象
 *
 * @author JavierChen
 */
@Data
@Builder
@Schema(description = "测验题目")
public class QuestionVO {

    @Schema(description = "题目ID")
    private UUID id;

    @Schema(description = "题目编号")
    private Integer questionNo;

    @Schema(description = "题目类型")
    private QuestionType questionType;

    @Schema(description = "题目文本")
    private String questionText;

    @Schema(description = "选项列表")
    private List<String> options;

    @Schema(description = "难度")
    private Difficulty difficulty;

    @Schema(description = "关联知识点")
    private String relatedConcept;

    @Schema(description = "是否已作答")
    private Boolean answered;
}
