package fun.javierchen.jcaiagentbackend.model.entity.quiz;

import fun.javierchen.jcaiagentbackend.model.entity.enums.GapType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Severity;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 未掌握知识实体 (知识缺口)
 * 结构化存储用户的知识缺口信息
 *
 * @author JavierChen
 */
@Entity
@Table(name = "unmastered_knowledge", indexes = {
        @Index(name = "idx_unmastered_knowledge_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_unmastered_knowledge_status", columnList = "status"),
        @Index(name = "idx_unmastered_knowledge_concept", columnList = "concept_name")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class UnmasteredKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ==================== 知识缺口信息 ====================

    /**
     * 概念名称
     */
    @Column(name = "concept_name", nullable = false, length = 256)
    private String conceptName;

    /**
     * 缺口类型
     * - CONCEPTUAL: 概念性缺口 (不理解基本概念)
     * - PROCEDURAL: 程序性缺口 (不知道如何操作/应用)
     * - BOUNDARY: 边界性缺口 (不清楚概念的边界/限制)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gap_type", length = 32)
    private GapType gapType;

    /**
     * AI 生成的缺口描述
     * 如: "混淆了抽象类和接口的区别"
     */
    @Column(name = "gap_description", columnDefinition = "TEXT")
    private String gapDescription;

    /**
     * 可能的根本原因
     */
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    /**
     * 严重程度
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 32)
    private Severity severity;

    // ==================== 来源信息 ====================

    /**
     * 来源文档ID
     */
    @Column(name = "source_doc_id")
    private Long sourceDocId;

    /**
     * 来源测验会话ID
     */
    @Column(name = "source_session_id")
    private UUID sourceSessionId;

    // ==================== 状态信息 ====================

    /**
     * 缺口状态
     * - ACTIVE: 活跃 (尚未解决)
     * - RESOLVED: 已解决
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private KnowledgeGapStatus status = KnowledgeGapStatus.ACTIVE;

    /**
     * 失败次数 (在该知识点上的错误次数)
     */
    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 1;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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
     * 判断是否为活跃缺口
     */
    public boolean isActive() {
        return status == KnowledgeGapStatus.ACTIVE;
    }

    /**
     * 判断是否已解决
     */
    public boolean isResolved() {
        return status == KnowledgeGapStatus.RESOLVED;
    }

    /**
     * 标记为已解决
     */
    public void markAsResolved() {
        this.status = KnowledgeGapStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 增加失败计数
     */
    public void incrementFailureCount() {
        this.failureCount++;
        // 失败3次以上自动提升严重程度
        if (this.failureCount >= 3 && this.severity == Severity.LOW) {
            this.severity = Severity.MEDIUM;
        } else if (this.failureCount >= 5 && this.severity == Severity.MEDIUM) {
            this.severity = Severity.HIGH;
        }
    }

    /**
     * 判断是否为高优先级缺口
     */
    public boolean isHighPriority() {
        return severity == Severity.HIGH || failureCount >= 5;
    }

    /**
     * 获取优先级分数 (用于排序)
     * 综合考虑严重程度和失败次数
     */
    public int getPriorityScore() {
        int severityScore = severity != null ? severity.getPriority() * 10 : 0;
        return severityScore + Math.min(failureCount, 10);
    }
}
