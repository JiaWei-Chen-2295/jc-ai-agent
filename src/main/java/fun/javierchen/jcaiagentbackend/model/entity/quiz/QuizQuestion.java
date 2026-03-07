package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 测验题目实体
 * 存储 Agent 生成的题目
 *
 * @author JavierChen
 */
@Entity
@Table(name = "quiz_question", indexes = {
        @Index(name = "idx_quiz_question_session", columnList = "session_id"),
        @Index(name = "idx_quiz_question_tenant", columnList = "tenant_id"),
        @Index(name = "idx_quiz_question_type", columnList = "question_type")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * 题目编号
     */
    @Column(name = "question_no", nullable = false)
    private Integer questionNo;

    /**
     * 题目文本
     */
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /**
     * 题目类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    /**
     * 选项数据 (JSONB -> List<String>)
     * - 选择题: ["A选项", "B选项", "C选项", "D选项"]
     * - 连线题: [{"left": "Java", "right": "面向对象"}, ...]
     * - 判断题/填空题/主观题: null
     */
    @Type(JsonBinaryType.class)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<String> options;

    /**
     * 正确答案
     * - 单选题: "B"
     * - 多选题: "A,C,D"
     * - 判断题: "TRUE" 或 "FALSE"
     * - 填空题: "答案1;答案2" (多空用分号分隔)
     * - 排序题: "3,1,2,4" (正确顺序的索引)
     * - 其他: 参考答案文本
     */
    @Column(name = "correct_answer", nullable = false, columnDefinition = "TEXT")
    private String correctAnswer;

    /**
     * 答案解释
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    /**
     * 关联的知识点名称
     * 用于知识缺口分析
     */
    @Column(name = "related_concept", length = 256)
    private String relatedConcept;

    /**
     * 来源文档ID
     */
    @Column(name = "source_doc_id")
    private Long sourceDocId;

    /**
     * 来源向量块ID (关联 study_friends)
     */
    @Column(name = "source_chunk_id")
    private UUID sourceChunkId;

    /**
     * 难度等级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 32)
    private Difficulty difficulty = Difficulty.MEDIUM;

    /**
     * 创建时间 (自动填充)
     */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间 (自动填充)
     */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 软删除标记
     */
    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;

    // ==================== 关联关系 ====================

    /**
     * 所属会话 (多对一)
     * 不使用数据库外键
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private QuizSession session;

    /**
     * 题目的答题记录 (一对多)
     */
    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<QuestionResponse> responses = new ArrayList<>();

    // ==================== 便捷方法 ====================

    /**
     * 判断是否需要选项
     */
    public boolean requiresOptions() {
        return questionType != null && questionType.requiresOptions();
    }

    /**
     * 判断是否为客观题
     */
    public boolean isObjective() {
        return questionType != null && questionType.isObjective();
    }

    /**
     * 判断是否为主观题
     */
    public boolean isSubjective() {
        return questionType != null && questionType.isSubjective();
    }

    /**
     * 判断是否支持部分得分
     */
    public boolean supportsPartialScore() {
        return questionType != null && questionType.supportsPartialScore();
    }
}
