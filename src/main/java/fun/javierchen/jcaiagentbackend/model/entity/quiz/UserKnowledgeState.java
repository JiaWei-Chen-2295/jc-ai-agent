package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户知识状态实体
 * 三维认知模型: 理解深度 + 认知负荷 + 稳定性
 *
 * @author JavierChen
 */
@Entity
@Table(name = "user_knowledge_state", indexes = {
        @Index(name = "idx_user_knowledge_state_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_user_knowledge_state_topic", columnList = "topic_type, topic_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_knowledge_state", columnNames = { "tenant_id", "user_id", "topic_type",
                "topic_id" })
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class UserKnowledgeState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ==================== 知识主题 ====================

    /**
     * 主题类型 (DOCUMENT: 文档, CONCEPT: 概念)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "topic_type", nullable = false, length = 32)
    private TopicType topicType;

    /**
     * 主题ID
     * - 文档: 文档ID的字符串表示
     * - 概念: 概念名称的哈希或直接使用名称
     */
    @Column(name = "topic_id", nullable = false, length = 128)
    private String topicId;

    /**
     * 主题可读名称
     */
    @Column(name = "topic_name", length = 256)
    private String topicName;

    // ==================== 三维认知模型 (0-100) ====================

    /**
     * 理解深度 (Understanding Depth)
     * - 衡量用户是否理解概念本质
     * - 通过解释题、关联推理题评估
     * - 达标阈值: ≥ 70
     */
    @Column(name = "understanding_depth", nullable = false)
    private Integer understandingDepth = 50;

    /**
     * 认知负荷 (Cognitive Load)
     * - 衡量用户答题是否吃力
     * - 通过响应时间、犹豫标记评估
     * - 达标阈值: ≤ 40 (越低越好)
     */
    @Column(name = "cognitive_load_score", nullable = false)
    private Integer cognitiveLoadScore = 50;

    /**
     * 稳定性 (Stability)
     * - 衡量用户是否反复犯同类错误
     * - 通过错误重复率计算
     * - 达标阈值: ≥ 70
     */
    @Column(name = "stability_score", nullable = false)
    private Integer stabilityScore = 50;

    // ==================== 统计数据 ====================

    /**
     * 该主题的总题目数
     */
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    /**
     * 该主题的正确回答数
     */
    @Column(name = "correct_answers", nullable = false)
    private Integer correctAnswers = 0;

    // ==================== 时间戳 ====================

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;

    // ==================== 便捷方法 ====================

    /**
     * 判断是否已掌握该知识点
     * 条件: D ≥ 70 AND L ≤ 40 AND S ≥ 70
     */
    public boolean isMastered() {
        return understandingDepth >= 70
                && cognitiveLoadScore <= 40
                && stabilityScore >= 70;
    }

    /**
     * 判断理解深度是否达标
     */
    public boolean isDepthReached() {
        return understandingDepth >= 70;
    }

    /**
     * 判断认知负荷是否达标 (轻松状态)
     */
    public boolean isLoadLow() {
        return cognitiveLoadScore <= 40;
    }

    /**
     * 判断稳定性是否达标
     */
    public boolean isStable() {
        return stabilityScore >= 70;
    }

    /**
     * 判断是否处于挣扎状态 (高负荷 + 低稳定性)
     */
    public boolean isStruggling() {
        return cognitiveLoadScore > 60 && stabilityScore < 50;
    }

    /**
     * 判断是否可以提高难度 (低负荷 + 高深度)
     */
    public boolean canIncreaseDifficulty() {
        return cognitiveLoadScore < 30 && understandingDepth >= 70;
    }

    /**
     * 计算正确率
     */
    public double getAccuracyRate() {
        if (totalQuestions == 0)
            return 0.0;
        return (double) correctAnswers / totalQuestions;
    }

    /**
     * 计算综合掌握分数
     * 加权平均: 理解深度40% + (100-认知负荷)30% + 稳定性30%
     */
    public double getOverallScore() {
        return understandingDepth * 0.4
                + (100 - cognitiveLoadScore) * 0.3
                + stabilityScore * 0.3;
    }

    /**
     * 更新三维指标
     */
    public void updateScores(int depth, int load, int stability) {
        // 使用加权移动平均，新值权重0.3
        this.understandingDepth = (int) (this.understandingDepth * 0.7 + depth * 0.3);
        this.cognitiveLoadScore = (int) (this.cognitiveLoadScore * 0.7 + load * 0.3);
        this.stabilityScore = (int) (this.stabilityScore * 0.7 + stability * 0.3);

        // 边界检查
        this.understandingDepth = Math.max(0, Math.min(100, this.understandingDepth));
        this.cognitiveLoadScore = Math.max(0, Math.min(100, this.cognitiveLoadScore));
        this.stabilityScore = Math.max(0, Math.min(100, this.stabilityScore));
    }
}
