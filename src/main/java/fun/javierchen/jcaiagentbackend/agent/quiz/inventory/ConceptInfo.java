package fun.javierchen.jcaiagentbackend.agent.quiz.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 概念信息 DTO
 *
 * @author JavierChen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConceptInfo {

    /**
     * 概念名称
     */
    private String name;

    /**
     * 来源文档 ID
     */
    private Long sourceDocId;
}
