package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测验会话实体
 * 管理测验会话生命周期
 *
 * @author JavierChen
 */
@Entity
@Table(name = "quiz_session", indexes = {
        @Index(name = "idx_quiz_session_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_quiz_session_status", columnList = "status"),
        @Index(name = "idx_quiz_session_user_time", columnList = "tenant_id, user_id, create_time")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 测验模式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_mode", nullable = false, length = 32)
    private QuizMode quizMode = QuizMode.ADAPTIVE;

    /**
     * 涉及的文档ID列表 (JSONB -> List<Long>)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "document_scope", columnDefinition = "jsonb")
    private List<Long> documentScope;

    /**
     * 会话状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuizStatus status = QuizStatus.IN_PROGRESS;

    /**
     * 当前题目编号
     */
    @Column(name = "current_question_no", nullable = false)
    private Integer currentQuestionNo = 0;

    /**
     * 总题目数
     */
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    /**
     * 得分
     */
    @Column(name = "score", nullable = false)
    private Integer score = 0;

    /**
     * Agent 状态快照 (JSONB -> Map<String, Object>)
     * 灵活存储 Agent 运行时状态
     */
    @Type(JsonBinaryType.class)
    @Column(name = "agent_state", columnDefinition = "jsonb")
    private Map<String, Object> agentState;

    /**
     * 测验开始时间
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 测验完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
     * 软删除标记 (0: 未删除, 1: 已删除)
     */
    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;

    // ==================== 关联关系 ====================

    /**
     * 会话包含的题目 (一对多)
     */
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<QuizQuestion> questions = new ArrayList<>();

    /**
     * 会话的答题记录 (一对多)
     */
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<QuestionResponse> responses = new ArrayList<>();

    /**
     * 会话的 Agent 执行日志 (一对多)
     */
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AgentExecutionLog> executionLogs = new ArrayList<>();

    // ==================== 便捷方法 ====================

    /**
     * 添加题目到会话
     */
    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setSession(this);
    }

    /**
     * 添加答题记录到会话
     */
    public void addResponse(QuestionResponse response) {
        responses.add(response);
        response.setSession(this);
    }

    /**
     * 判断会话是否可继续
     */
    public boolean canContinue() {
        return status == QuizStatus.IN_PROGRESS || status == QuizStatus.PAUSED;
    }

    /**
     * 判断会话是否已结束
     */
    public boolean isFinished() {
        return status.isTerminal();
    }

    /**
     * 计算正确率
     */
    public double getAccuracyRate() {
        if (totalQuestions == 0)
            return 0.0;
        return (double) score / totalQuestions;
    }
}
