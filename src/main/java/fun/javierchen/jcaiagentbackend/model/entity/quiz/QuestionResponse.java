package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 题目回答实体
 * 记录用户的每次回答，用于认知分析
 *
 * @author JavierChen
 */
@Entity
@Table(name = "question_response", indexes = {
        @Index(name = "idx_question_response_session", columnList = "session_id"),
        @Index(name = "idx_question_response_question", columnList = "question_id"),
        @Index(name = "idx_question_response_user", columnList = "user_id")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class QuestionResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 用户答案
     */
    @Column(name = "user_answer", nullable = false, columnDefinition = "TEXT")
    private String userAnswer;

    /**
     * 是否正确
     */
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    /**
     * 得分 (0-100)
     * 支持部分正确的情况
     */
    @Column(name = "score", nullable = false)
    private Integer score = 0;

    // ==================== 认知指标 ====================

    /**
     * 响应时间 (毫秒)
     * 用于计算认知负荷
     */
    @Column(name = "response_time_ms", nullable = false)
    private Integer responseTimeMs;

    /**
     * 是否检测到犹豫
     * 如用户表达 "不知道"、"不确定" 等
     */
    @Column(name = "hesitation_detected", nullable = false)
    private Boolean hesitationDetected = false;

    /**
     * 是否检测到困惑
     */
    @Column(name = "confusion_detected", nullable = false)
    private Boolean confusionDetected = false;

    // ==================== Agent 反馈 ====================

    /**
     * Agent 生成的反馈
     */
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    /**
     * Agent 在此回答后的决策
     * CONTINUE: 继续出题
     * REMEDIATE: 进入补漏模式
     * EXPAND: 扩展难度
     * FINISH: 结束测验
     */
    @Column(name = "agent_action", length = 32)
    private String agentAction;

    /**
     * 概念掌握程度
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "concept_mastery", length = 32)
    private ConceptMastery conceptMastery;

    // ==================== 时间戳 ====================

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;

    // ==================== 关联关系 ====================

    /**
     * 所属会话 (多对一)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private QuizSession session;

    /**
     * 关联的题目 (多对一)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private QuizQuestion question;

    // ==================== 便捷方法 ====================

    /**
     * 判断是否需要补漏
     */
    public boolean needsRemediation() {
        return conceptMastery != null && conceptMastery.needsRemediation();
    }

    /**
     * 计算认知负荷信号强度
     */
    public int getCognitiveLoadSignal() {
        int signal = 0;
        if (Boolean.TRUE.equals(hesitationDetected))
            signal += 30;
        if (Boolean.TRUE.equals(confusionDetected))
            signal += 40;
        if (Boolean.FALSE.equals(isCorrect))
            signal += 20;
        return Math.min(100, signal);
    }

    /**
     * 判断响应时间是否偏长
     * 
     * @param avgResponseTimeMs 平均响应时间
     */
    public boolean isSlowResponse(int avgResponseTimeMs) {
        return responseTimeMs > avgResponseTimeMs * 1.5;
    }
}
